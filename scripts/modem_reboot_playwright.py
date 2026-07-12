import time
import requests
import subprocess
import re
import base64
from playwright.sync_api import sync_playwright

def get_my_ip():
    urls = [
        'https://api.ipify.org',
        'https://ifconfig.me/ip',
        'https://ipinfo.io/ip'
    ]
    for url in urls:
        try:
            print(f"尝试从 {url} 获取公网 IP...")
            response = requests.get(url, timeout=10)
            if response.status_code == 200:
                ip = response.text.strip()
                if len(ip) < 50 and re.match(r'^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$', ip):
                    return ip
        except Exception as e:
            print(f"获取 IP 失败 ({url}): {e}")

    try:
        print("尝试使用网页抓取 lddgo 的 IP...")
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto('https://www.lddgo.net/network/getmyip', timeout=20000)
            text = page.locator('body').inner_text()
            ip_match = re.search(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', text)
            browser.close()
            if ip_match:
                return ip_match.group(0)
    except Exception as e:
        print(f"Playwright 获取 IP 失败: {e}")
    return None

def ping_test(host='192.168.1.1'):
    try:
        output = subprocess.run(['ping', '-n', '1', host], capture_output=True, text=True, timeout=3)
        return output.returncode == 0
    except Exception:
        return False

def reboot_router():
    print("开始连接光猫后台...")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        try:
            page.goto('http://192.168.1.1', timeout=15000)
            page.wait_for_load_state('networkidle')

            # --- 步骤 2.1-2.2：账户选择与表单填充 ---
            print("已经打开登录页。正在定位元素...")

            # 分析 HTML 发现：
            # 正常用户登录容器：#normaluser。用户名框：#txt_normalUsername，密码框：#txt_normalPassword
            # 按钮通过 onclick="SubmitForm();"，也可以直接 press('Enter')
            # 顺便找“普通用户”按钮点击切换（如果还没显示）：通常默认显示
            # 这里我们要：点击 normalphoto (普通用户)，如果没选中的话
            if page.locator('#normalphoto').is_visible():
                print("点击普通用户登录入口 (#normalphoto)...")
                page.click('#normalphoto')
                time.sleep(1)

            # 输入用户名和密码
            print("填充普通账户 user 及密码...")
            page.fill('#txt_normalUsername', 'user')
            page.fill('#txt_normalPassword', '9fchpda2')

            # 点击登录（密码框右侧的 div 可以触发 SubmitForm）
            print("提交登录表单...")
            # 可以直接对密码框输入 Enter 触发 Submitting
            page.keyboard.press("Enter")

            page.wait_for_load_state('networkidle')
            time.sleep(5) # 稍微等等，等待跳转
            print(f"当前 URL: {page.url}")

            # 接着执行重启部分。先写个大致的多框架兼容抓取（光猫可能跳转到另一个网页，比如 main.asp / cu.html，或者是含 iframe 的模版页）
            # 我们先在这打印当前 URL 和可能的主体，后续如果出错，保存源码以分析内页结构。

            # 通常华为或联通光猫管理后台内页可能通过 iframe 渲染，也可能直接渲染：
            # 这里先匹配可能菜单：
            # 华为/联通网关菜单通常具有 '管理', '设备管理', '重启'

            # 我们在 page 对象中寻找包含“管理”文本的按钮并点击
            # 1. 尝试寻找顶部导航的“管理”
            main_selectors = [
                'text="管理"',
                'a:has-text("管理")',
                'li:has-text("管理")',
                '#menu_manage',
                '#manage'
            ]

            manage_clicked = False
            for s in main_selectors:
                try:
                    # 有些光猫是在 iframe 里面
                    # 华为的光猫经常用 `frame` 或 `iframe` 塞着。我们检测一下是否有 iframe
                    frames = page.frames
                    print(f"当前页面包含 {len(frames)} 个 frames")
                    for frame in frames:
                        if frame.locator(s).is_visible():
                            frame.locator(s).click()
                            print(f"[Frame] 成功点击 '管理' 菜单: {s}")
                            manage_clicked = True
                            break
                    if manage_clicked:
                        break

                    if page.locator(s).is_visible():
                        page.locator(s).click()
                        print(f"直接页面 成功点击 '管理' 菜单: {s}")
                        manage_clicked = True
                        break
                except Exception:
                    continue

            if not manage_clicked:
                print("未立即找到或点击 '管理' 菜单，将保存内页源码以供分析。")
                raise Exception("无法导航到管理页")

            time.sleep(2)
            page.wait_for_load_state('networkidle')

            # 2. 侧边栏“设备管理”
            dev_selectors = [
                'text="设备管理"',
                'a:has-text("设备管理")',
                'li:has-text("设备管理")',
                '#dev_manage'
            ]
            dev_clicked = False
            for s in dev_selectors:
                try:
                    for frame in page.frames:
                        if frame.locator(s).is_visible():
                            frame.locator(s).click()
                            print(f"[Frame] 成功点击 '设备管理' 侧边栏: {s}")
                            dev_clicked = True
                            break
                    if dev_clicked:
                        break
                    if page.locator(s).is_visible():
                        page.locator(s).click()
                        print(f"直接页面 成功点击 '设备管理' 侧边栏: {s}")
                        dev_clicked = True
                        break
                except Exception:
                    continue

            if not dev_clicked:
                raise Exception("无法导航到设备管理页")

            time.sleep(2)
            page.wait_for_load_state('networkidle')

            # 3. 点击“重启”按钮并处理弹窗
            def handle_dialog(dialog):
                print(f"捕获到弹窗: [{dialog.message}]，准备接受(确定)。")
                dialog.accept()
            page.on('dialog', handle_dialog)

            reboot_selectors = [
                'input[type="button"][value="重启"]',
                'input[type="button"][value="重启设备"]',
                'button:has-text("重启")',
                'button:has-text("重启设备")',
                '#reboot',
                '#btn_reboot',
                'input[value="设备重启"]'
            ]
            reboot_clicked = False
            for s in reboot_selectors:
                try:
                    for frame in page.frames:
                        if frame.locator(s).is_visible():
                            frame.locator(s).click()
                            print(f"[Frame] 成功点击 '重启设备' 按钮: {s}")
                            reboot_clicked = True
                            break
                    if reboot_clicked:
                        break
                    if page.locator(s).is_visible():
                        page.locator(s).click()
                        print(f"直接页面 成功点击 '重启设备' 按钮: {s}")
                        reboot_clicked = True
                        break
                except Exception:
                    continue

            if not reboot_clicked:
                raise Exception("无法找到并点击重启按钮")

            time.sleep(5)
            print("重启指令已发送。")

        except Exception as e:
            # 报错时保存页面源码供分析
            try:
                # 递归保存所有 frame 的源码
                for i, frame in enumerate(page.frames):
                    with open(f"router_inner_frame_{i}.html", "w", encoding="utf-8") as f:
                        f.write(frame.content())
                with open("router_inner_page.html", "w", encoding="utf-8") as f:
                    f.write(page.content())
                print("出错！已将当前内页和所有 frame 源码保存至 router_inner_page.html / router_inner_frame_*.html。")
            except Exception as se:
                print(f"保存内页源码失败: {se}")
            raise e
        finally:
            browser.close()

def main():
    print("================== 开始光猫测试自动化 ==================")
    old_ip = get_my_ip()
    print(f"【第一步】重启前公网 IP: {old_ip}")
    if not old_ip:
        print("警告：无法获取重启前的公网 IP，将继续执行。")
        old_ip = "未知"

    try:
        # 验证光猫网关是否可达
        if not ping_test('192.168.1.1'):
            print("错误: 无法 ping 通光猫网关 192.168.1.1，请检查物理连线。")
            return "Failed (Gateway unreachable)", old_ip, "未知"

        # 步骤 2
        reboot_router()

        # 步骤 3
        print("【第三步】等待网络断开（光猫关机中）...")
        disconnected = False
        for _ in range(30):
            if not ping_test('192.168.1.1'):
                print("成功检测到网络断开，光猫开始重启。")
                disconnected = True
                break
            time.sleep(1)

        if not disconnected:
            print("提示：在等待时间内未检测到网关 192.168.1.1 断开。可能是快速重启，也可能指令未生效。")

        print("【第三步续】等待网络重新连通并恢复公网访问...")
        connected = False
        for i in range(60):
            time.sleep(10)
            if ping_test('8.8.8.8') or ping_test('114.114.114.114'):
                print("网络重新连通！")
                connected = True
                break
            print(f"网络未连通，继续等待... (阶段 {i+1}/60)")

        if not connected:
            print("错误：光猫在10分钟内没有恢复网络连接。")
            return "Failed (Network recovery timeout)", old_ip, "未知"

        # 步骤 4
        print("【第四步】开始获取重启后的公网 IP...")
        new_ip = None
        for _ in range(6):
            new_ip = get_my_ip()
            if new_ip:
                break
            print("可能尚未完全拨号成功，等待10秒后重试...")
            time.sleep(10)

        print(f"【第四步续】重启后公网 IP: {new_ip}")

        if not new_ip:
            return "Failed (Cannot fetch new IP)", old_ip, "未知"

        if old_ip != "未知" and old_ip == new_ip:
            print("对比结论: IP 未改变。运营商未释放旧 IP 或拨号未触发重新分配。")
            return "Failed (IP remained identical)", old_ip, new_ip
        else:
            print("对比结论: IP 切换成功！")
            return "Success", old_ip, new_ip

    except Exception as e:
        print(f"执行异常中止: {e}")
        return f"Failed ({str(e)})", old_ip, "未知"

if __name__ == '__main__':
    result, old_ip, new_ip = main()
    print(f"\n================== 任务最终结果: {result} ==================")
    print(f"OLD_IP: {old_ip}")
    print(f"NEW_IP: {new_ip}")
