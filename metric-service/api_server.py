# metric-service 的 HTTP 服务壳：供 Java 服务直连调用（不进 Nacos）
# 启动：python api_server.py    （默认端口 8001）
#
# 设计要点（老师强调的"离线评测"）：
#   POST /evaluate 立即返回 task_id，打分在后台线程慢慢跑（要调裁判 LLM 几十次、耗时几十秒），
#   调用方用 GET /task/{task_id} 轮询结果——热路径上不阻塞。
import threading
import uuid

import uvicorn
from datasets import Dataset
from fastapi import FastAPI
from pydantic import BaseModel
from ragas import evaluate

from otel_setup import tracer  # 跨语言追踪：把评测 Span 上报到 SkyWalking
from ragsa_eval import build_judge_and_embeddings, build_metrics

app = FastAPI(title="metric-service", description="RAGAS 离线评测服务（RAG Triad）")

# 评测任务表：task_id -> {"status": running/done/error, "scores"?: {...}, "message"?: ...}
TASKS = {}

# 裁判模型 + 向量模型 + 三个指标（复用评测脚本里的构造，含中文/DeepSeek-n=1 适配）
judge_llm, embeddings = build_judge_and_embeddings()
metrics = build_metrics()


class EvalRequest(BaseModel):
    """一次 RAG 问答的四个字段：question/contexts/answer/ground_truth"""
    question: str
    contexts: list[str]          # 检索到的上下文列表
    answer: str                  # 模型生成的答案
    ground_truth: str = ""       # 参考答案（判分基准）；为空时跳过 context_precision


def _run_eval(task_id: str, req: EvalRequest) -> None:
    try:
        use_metrics = metrics
        eval_data = Dataset.from_dict({
            "question": [req.question],
            "contexts": [req.contexts],
            "answer": [req.answer],
            # context_precision 必须有参考答案；没有就只评另外两个指标
            "ground_truth": [req.ground_truth or req.answer],
        })
        if not req.ground_truth:
            # 只评 faithfulness + answer_relevancy（都是 metrics 列表里的前两个）
            use_metrics = metrics[:2]

        with tracer.start_as_current_span("ragas-evaluate") as span:
            span.set_attribute("metrics", "faithfulness,answer_relevancy,context_precision")
            result = evaluate(eval_data, metrics=use_metrics, llm=judge_llm, embeddings=embeddings)
        df = result.to_pandas()

        scores = {}
        for col in df.columns:
            if col in ("question", "contexts", "answer", "ground_truth"):
                continue
            value = df[col].iloc[0]
            # NaN 转 None，保证 JSON 可序列化
            scores[col] = None if (value is None or value != value) else round(float(value), 4)
        TASKS[task_id] = {"status": "done", "scores": scores}
    except Exception as e:  # noqa: BLE001
        import traceback
        traceback.print_exc()  # 完整堆栈打到服务端控制台，方便排错
        TASKS[task_id] = {"status": "error", "message": str(e)}


@app.post("/evaluate")
def create_eval(req: EvalRequest):
    """提交一次评测：立即返回 task_id，后台异步打分"""
    task_id = uuid.uuid4().hex[:12]
    TASKS[task_id] = {"status": "running"}
    threading.Thread(target=_run_eval, args=(task_id, req), daemon=True).start()
    return {"task_id": task_id, "status": "running",
            "hint": f"轮询 GET /task/{task_id} 获取结果（约 30~60 秒）"}


@app.get("/task/{task_id}")
def get_task(task_id: str):
    """查询评测结果"""
    return TASKS.get(task_id, {"status": "not_found"})


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8090)  # 按需求文档对齐：metric-service = 8090
