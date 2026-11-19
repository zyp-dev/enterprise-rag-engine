# otel_setup.py —— 初始化 OpenTelemetry，上报到 SkyWalking（跨 Java/Python 链路）
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.resources import Resource

# 1. 服务名用 metric-service（Python 评测服务），和 Java 侧命名统一
resource = Resource.create({"service.name": "metric-service"})
provider = TracerProvider(resource=resource)

# 2. 用 OTLP gRPC 协议把 Span 上报到 SkyWalking 的 11800（和 Java Agent 同一个端口）
#    insecure=True 表示走明文（无 TLS）：SkyWalking 的 11800 是明文 gRPC，不加密；
#    不写这个参数，OTLP 导出器默认走 TLS，会报 Tls handshake failed / WRONG_VERSION_NUMBER
#    用 SimpleSpanProcessor 而非 BatchSpanProcessor：Batch 是「攒一批(默认 5 秒)再发」，
#    脚本跑完就退出会来不及 flush、span 直接丢；Simple 每个 span 立即上报，demo 更稳
provider.add_span_processor(
    SimpleSpanProcessor(OTLPSpanExporter(endpoint="localhost:11800", insecure=True))
)

# 3. 设为全局 TracerProvider
trace.set_tracer_provider(provider)
tracer = trace.get_tracer(__name__)
