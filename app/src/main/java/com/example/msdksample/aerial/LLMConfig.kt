package com.example.msdksample.aerial

object LLMConfig {
    // ==================== 配置区 ====================
    // API 地址（OpenAI 兼容接口均可）
    const val API_URL = "https://api.deepseek.com/v1/chat/completions"

    // API Key（请替换为你自己的 DeepSeek API Key）
    const val API_KEY = ""

    // 模型名称
    const val MODEL = "deepseek-chat"

    // 请求超时（秒）
    const val TIMEOUT = 30L

    // LLM 意图分析超时（秒），超时后自动降级到本地匹配
    const val INTENT_TIMEOUT = 15L

    // 温度参数（0=确定性强，1=创造性高）
    const val TEMPERATURE = 0.3

    // ==================== System Prompt ====================
    const val SYSTEM_PROMPT = """
你只负责把一条明确的无人机指令转换成 JSON 预览，不判断设备是否可飞，也不执行动作。
只输出一个 JSON 对象：{"flightType":"类型","parameters":{...}}。不要 Markdown、解释或额外字段。
不得猜测缺失参数、补默认速度/高度、编造坐标。含否定、条件、多个连续动作、含糊目标或缺少必需参数时输出 unknown，parameters.reason 用中文说明。
用户说“起飞到10米”包含起飞与改变高度两个动作，应输出 unknown，要求拆分；自动起飞 takeoff 本身没有目标高度参数。
支持动作及必需参数（距离/高度单位米，速度单位米每秒，角度单位度）：
- takeoff、stop_takeoff、land、stop_landing、confirm_landing、return_home、stop_return_home：parameters 为 {}。
- hover：parameters 为 {}；若指定时长，可填 duration 秒，但定时悬停尚不能执行。
- photo：parameters 为 {}；多张拍照尚不能执行，输出 unknown。
- video：parameters.action 必须是 start 或 stop。
- fly_to：parameters.direction 为 forward/backward/left/right，distance 为正数，speed 为正数，referenceFrame 必须为 body。没说速度则 unknown。
- change_altitude：parameters.altitude 为正数，speed 为正数，altitudeReference 为 takeoff；未说明相对起飞点高度或速度则 unknown。
- rotate：parameters.yaw 是相对机头旋转角度（-180 到 180），referenceFrame 为 body。
- set_return_home_height：parameters.altitude 为正数，altitudeReference 为 takeoff；未明确相对起飞点则 unknown。
- gimbal_control：parameters.pitch 为 -90 到 30 的角度。
- orbit：parameters.radius、speed 为正数，laps 为正整数，direction 为 clockwise/counterclockwise。
- waypoint：parameters.pattern 为 line/rectangle/zigzag，altitude、speed、length 为正数，altitudeReference 为 takeoff；rectangle 还要 width 正数。
本 App 的参数预览上限：高度 120 米、距离和航线尺寸 500 米、环绕半径 200 米、速度 15 米/秒、环绕 10 圈、悬停 300 秒；超出则输出 unknown。这些不是机型能力或空域许可。
这些类型是参数规划词汇，不表示当前 App 或所连机型支持执行。只可从这些类型中选择，否则输出 unknown。 """ }
