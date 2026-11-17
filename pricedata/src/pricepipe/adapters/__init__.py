"""官方商城 adapter 包。

约定（见 pricedata/README.md §5）:
- 每个 adapter 模块暴露 ``ADAPTER_ID`` / ``fetch()`` / ``parse()``；
- 低频、可关闭；失败不阻断其他来源；不绕过登录/验证码；
- ``parse()`` 是纯函数，测试用本地 fixture，**不联网**。

注册表 ``ADAPTERS`` 供 ``sources.yml`` 的 ``adapter:`` 字段解析。
"""
from __future__ import annotations

from . import colorful_store

ADAPTERS = {
    colorful_store.ADAPTER_ID: colorful_store,
}
