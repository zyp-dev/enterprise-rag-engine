# ⑤ 完整评测链路：出题 → RAG检索生成 → 评测打分（闭环）
from datasets import Dataset
from ragas import evaluate

from generate_dataset import generate_qa_pairs  # 第④节：自动出题
from ragsa_eval import (  # 第③节：裁判模型+向量模型+评测指标
    build_judge_and_embeddings,
    build_metrics,
)


def main():
    # 1. 自动出题【得到 question + ground_truth】
    #    ground_truth 是判分的基准：人工标注成本高，用 LLM 自动生成
    docs = ["公司年假政策：入职满一年享受5天年假……"]
    qa = generate_qa_pairs(docs, n=3)
    questions = [x["question"] for x in qa]
    ground_truth = [x["answer"] for x in qa]

    # 2. 跑 RAG 系统，得到 contexts + answer
    #    真实项目里：contexts 由 search-service（混合检索/Text2SQL）检索得到，
    #               answer 由 ai-service 调用 LLM 生成得到。
    #    这里先用模拟数据占位，接入真实服务时替换下面两行即可。
    contexts = [["年假：入职满一年享受5天年假……"] for _ in questions]
    answer = ["入职满一年享受5天年假"] * len(questions)

    # 3. 裁判模型 + 向量模型【复用 ragsa_eval 里的函数，不重复写】
    judge_llm, embeddings = build_judge_and_embeddings()

    # 4. 组装数据集进行评测
    #    四个字段的来源：
    #    question     用户问题【真实提问或自动生成】
    #    ground_truth 参考答案【判分基准，人工标注或自动生成】
    #    contexts     检索到的上下文【RAG 系统真实检索一次得到】
    #    answer       模型生成的答案【RAG 系统真实生成一次得到】
    #    注意：RAGAS 只认单数 "question"，不是 "questions"
    eval_data = Dataset.from_dict(
        {
            "question": questions,
            "ground_truth": ground_truth,
            "contexts": contexts,
            "answer": answer,
        }
    )

    # 5. 评测：出题 → 检索生成 → 评测打分的完整闭环
    result = evaluate(
        eval_data,
        metrics=build_metrics(),  # 适配 DeepSeek(只支持 n=1) + 中文的指标构造
        llm=judge_llm,
        embeddings=embeddings,
    )

    # 6. 输出结果【每个指标 0~1 分数，越高越好】
    print("\n===== 完整链路评测结果 =====")
    print(result)
    print("\n===== 明细结果 =====")
    print(result.to_pandas())


if __name__ == "__main__":
    main()
