"""
光猫重启脚本 — 华为 HS8346X6 (联通)
Spider 连续 4 次 502 后调用此脚本换公网 IP

工作流程:
1. 访问重启页面 → 提取 ont token(动态生成)
2. POST 重启请求
3. 等待重启 + 获取新 IP
"""
import time
import re
import requests

MODEM_IP = "192.168.1.1"
REBOOT_PAGE = f"http://{MODEM_IP}/html/ssmp/restore/resetfactory.asp"
REBOOT_URL = (
    f"http://{MODEM_IP}/set.cgi"
    "?x=InternetGatewayDevice.X_HW_DEBUG.SMP.DM.ResetBoard"
    "&RequestFile=html/ssmp/restore/resetfactory.asp"
)

def get_public_ip():
    """获取当前公网 IP"""
    try:
        r = requests.get("http://ifconfig.me", timeout=10)
        return r.text.strip()
    except Exception:
        return None


def get_ont_token(session):
    """从重启页面提取 ont token(动态)"""
    try:
        r = session.get(REBOOT_PAGE, timeout=10, verify=False)
        r.raise_for_status()
        # 从 HTML 中提取 ont token
        match = re.search(r'<input[^>]+name=["\']onttoken["\'][^>]+value=["\']([^"\']+)["\']', r.text)
        if not match:
            match = re.search(r'<input[^>]+id=["\']hwonttoken["\'][^>]+value=["\']([^"\']+)["\']', r.text)
        token = match.group(1) if match else None
        if token:
            print(f"✅ 获取 ont token 成功(length={len(token)})")
        return token
    except Exception as e:
        print(f"❌ 获取 ont token 失败: {e}")
        return None


def reboot_modem():
    """
    重启光猫
    返回: True=成功, False=失败
    """
    session = requests.Session()
    session.verify = False

    # 首页访问触发登录(session cookie 就绪)
    print(f"📡 访问光猫主页 {MODEM_IP}...")
    try:
        session.get(f"http://{MODEM_IP}/", timeout=10)
    except Exception:
        pass

    # 获取 ont token
    print("🔑 获取 ont token...")
    ont_token = get_ont_token(session)

    if not ont_token:
        print("❌ 无法获取 ont token,可能未登录")
        return False

    # POST 重启请求
    print("🔄 提交重启请求...")
    try:
        resp = session.post(
            REBOOT_URL,
            data={"x.X_HW_Token": ont_token},
            timeout=15,
            allow_redirects=True
        )

        if resp.status_code in [200, 302]:
            print("✅ 重启请求已发送(HTTP 200/302)")
            return True
        else:
            print(f"⚠️ 异常状态码: {resp.status_code}")
            return False

    except Exception as e:
        print(f"❌ 重启请求失败: {e}")
        return False


def rotate_ip(max_retries=3):
    """
    完整 IP 轮换流程
    1. 记录旧 IP
    2. 重启光猫
    3. 等待重启完成(约 40 秒)
    4. 验证 IP 已变更
    返回: 新 IP 字符串(失败返回 None)
    """
    old_ip = get_public_ip()
    print(f"📌 当前公网 IP: {old_ip or 'unknown'}")

    if not old_ip:
        print("⚠️ 无法获取当前 IP,继续执行重启...")

    for attempt in range(1, max_retries + 1):
        print(f"\n===== 第 {attempt}/{max_retries} 次尝试 =====")

        if not reboot_modem():
            print(f"⏳ 等 10 秒后重试...")
            time.sleep(10)
            continue

        # 等待光猫重启(PPPoE 重新拨号约 20-30 秒)
        print("⏳ 等待 40 秒(光猫重启 + PPPoE 重拨)...")
        time.sleep(40)

        new_ip = get_public_ip()
        if new_ip and new_ip != old_ip:
            print(f"🎉 IP 已更换! {old_ip} → {new_ip}")
            return new_ip
        elif new_ip == old_ip:
            print(f"⚠️ IP 未变化,可能还在使用旧链接...")
            time.sleep(20)
        else:
            print(f"⚠️ 无法获取新 IP")

    return None


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "--check":
        # 仅查看当前 IP
        ip = get_public_ip()
        print(f"当前公网 IP: {ip}")
    elif len(sys.argv) > 1 and sys.argv[1] == "--token":
        # 仅测试获取 token(验证页面可访问性)
        session = requests.Session()
        session.verify = False
        session.get(f"http://{MODEM_IP}/", timeout=10)
        token = get_ont_token(session)
        print(f"ont token: {token}")
    else:
        # 执行 IP 轮换
        rotate_ip()
