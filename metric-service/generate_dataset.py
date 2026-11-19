# ④ 自动数据集：用 LLM 从文档自动生成评测数据集（问题+参考答案），省掉人工标注
import json

from openai import OpenAI

from config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL

# 复用 config 里配置的 LLM，创建 OpenAI 兼容客户端
client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)


def generate_qa_pairs(documents: list[str], n: int = 2) -> list[dict]:
    """
    从文档自动生成问答对，节省人工标注成本
    :param documents: 文档文本列表
    :param n: 每篇文档生成的问答对数量
    :return: [{"question":...,"answer":...},{...}]
    """
    qa_pairs = []
    for doc in documents:
        # 让 LLM 从文档【出题+给答案】
        resp = client.chat.completions.create(
            model=LLM_MODEL,  # 复用 config 配置的 LLM 模型
            messages=[
                {
                    "role": "system",
                    "content": "根据文档生成问答对，严格输出JSON数组：[{\"question\":\"...\",\"answer\":\"...\"}]",
                },
                {
                    "role": "user",
                    "content": f"文档：\n{doc}\n请生成{n}个问答对，答案必须来自文档内容。",
                },
            ],
        )
        # 解析 LLM 返回的 JSON，生产上用 json.loads + 容错
        content = resp.choices[0].message.content
        try:
            # 容错：LLM 有时会把 JSON 包在 ```json ... ``` 代码块里，先剥掉
            content = content.strip()
            if content.startswith("```"):
                content = content.strip("`")
                if content.startswith("json"):
                    content = content[len("json"):]
            pairs = json.loads(content)
            if isinstance(pairs, list):
                qa_pairs.extend(pairs)
            else:
                print("JSON 不是数组，跳过该文档：", content[:50])
        except json.JSONDecodeError:
            print("JSON解析失败，跳过该文档：", content[:50])
    return qa_pairs


if __name__ == "__main__":
    # 从知识库文档自动生成 10 个问答对（2 篇文档 × 每篇 5 个）
    docs = [
        "公司年假政策：入职满一年享受5天年假……",
        "差旅报销：提交发票后3个工作日到账……",
    ]
    qa_pairs = generate_qa_pairs(docs, n=5)
    for item in qa_pairs:
        print(item)
