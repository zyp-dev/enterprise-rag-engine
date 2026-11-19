import re

from ragas import evaluate
from ragas.metrics import AnswerRelevancy, ContextPrecision, Faithfulness
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from datasets import Dataset

from otel_setup import tracer  # 跨语言追踪：把评测 Span 上报到 SkyWalking

from config import (
    LLM_API_KEY,
    LLM_BASE_URL,
    LLM_MODEL,
    EMBEDDING_API_KEY,
    EMBEDDING_BASE_URL,
    EMBEDDING_MODEL,
)


def check_config():
    missing = []
    if not LLM_API_KEY:
        missing.append("LLM_API_KEY")
    if not LLM_BASE_URL:
        missing.append("LLM_BASE_URL")
    if not LLM_MODEL:
        missing.append("LLM_MODEL")
    if not EMBEDDING_API_KEY:
        missing.append("EMBEDDING_API_KEY")
    if not EMBEDDING_BASE_URL:
        missing.append("EMBEDDING_BASE_URL")
    if not EMBEDDING_MODEL:
        missing.append("EMBEDDING_MODEL")

    if missing:
        raise RuntimeError(
            "以下配置为空，请通过环境变量或 config.py 填写："
            + ", ".join(missing)
        )


def build_judge_and_embeddings():
    # 1. 裁判模型：用于 Faithfulness、Answer Relevance、Context Precision 打分
    judge_llm = LangchainLLMWrapper(
        ChatOpenAI(
            model=LLM_MODEL,
            base_url=LLM_BASE_URL,
            api_key=LLM_API_KEY,
            temperature=0,
        )
    )

    # 2. 向量模型：用于语义相似度相关指标
    # check_embedding_ctx_length=False：
    # langchain 默认会把文本 tokenize 成数字 ID 数组发给接口，
    # OpenAI 官方接口接受数字 ID，但千问/百炼兼容接口只接受字符串 → 必须关掉
    embeddings = LangchainEmbeddingsWrapper(
        OpenAIEmbeddings(
            model=EMBEDDING_MODEL,
            base_url=EMBEDDING_BASE_URL,
            api_key=EMBEDDING_API_KEY,
            check_embedding_ctx_length=False,
        )
    )

    return judge_llm, embeddings


class ChineseSentenceSegmenter:
    """中文句子切分器。

    RAGAS 0.1.x 的 faithfulness 只保留以英文句号 "." 结尾的句子
    （_faithfulness.py 里 `sentence.strip().endswith(".")`），
    中文句子没有 "."，会被全部过滤掉，导致 faithfulness = NaN。
    这里按中文标点切分，并给每句补一个 "." 来绕过这个过滤。
    """

    def segment(self, text: str) -> list[str]:
        parts = re.split(r"(?<=[。！？；])", text)
        sentences = []
        for p in parts:
            p = p.strip()
            if not p:
                continue
            if not p.endswith("."):
                p += "."
            sentences.append(p)
        return sentences


def build_metrics():
    """构造适配 DeepSeek + 中文评测的三个指标。

    1. faithfulness：传入中文切分器，绕过英文句号过滤问题
    2. answer_relevancy：默认 strictness=3 会以 n=3 调用 LLM，
       DeepSeek 只支持 n=1 → 改成 strictness=1
    3. context_precision：默认参数即可
    """
    return [
        Faithfulness(sentence_segmenter=ChineseSentenceSegmenter()),
        AnswerRelevancy(strictness=1),
        ContextPrecision(),
    ]


def main():
    check_config()
    judge_llm, embeddings = build_judge_and_embeddings()

    # 2. 准备评测数据集
    # RAGAS 0.1.x 常用字段：question、contexts、answer、ground_truth
    # 注意：contexts 必须是“列表的列表”，每个样本对应一个检索上下文列表
    eval_data = Dataset.from_dict(
        {
            "question": [
                "公司的年假政策是什么？",
                "如何报销差旅费？",
            ],
            "contexts": [
                ["年假：入职满一年享受5天年假……"],
                ["差旅报销需要提交发票……"],
            ],
            "answer": [
                "入职满一年享受5天年假",
                "提交发票即可报销",
            ],
            "ground_truth": [
                "入职满一年享受5天年假",
                "提交发票后3个工作日到账",
            ],
        }
    )

    # 3. 评测：用裁判 LLM 对每个指标打分，输出 0~1，越高越好
    # 三个指标的裁判逻辑与低分解读：
    # faithfulness       低 → 答案在编造（上下文中没有的依据写进了答案，幻觉）
    # answer_relevancy   低 → 答非所问（检索是对的，但生成跑偏了）
    # context_precision  低 → 检索环节没有召回相关内容（回去重新检索/重排）
    # 用 build_metrics() 构造：适配 DeepSeek(只支持 n=1) + 中文切分
    with tracer.start_as_current_span("ragas-evaluate") as span:
        span.set_attribute("metrics", "faithfulness,answer_relevancy,context_precision")
        result = evaluate(
            eval_data,
            metrics=build_metrics(),
            llm=judge_llm,
            embeddings=embeddings,
        )

    # 4. 输出结果
    print("\n===== 总体评测结果 =====")
    print(result)

    print("\n===== 明细结果 =====")
    print(result.to_pandas())


if __name__ == "__main__":
    main()