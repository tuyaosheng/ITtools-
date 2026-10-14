# -*- coding: utf-8 -*-
"""
ITtools 演示数据：在一个“空的”演示库里创建可直接测试的学校数据。
用法（先用 start-demo.bat 启动演示服务器）：
    python scripts/demo/seed_demo.py            # 默认 http://localhost:8300
    python scripts/demo/seed_demo.py http://服务器IP:8300
只使用 Python 标准库。同一个库只需运行一次；想重来先运行 reset-demo.bat。
"""
import http.cookiejar
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8300").rstrip("/")
HERE = os.path.dirname(os.path.abspath(__file__))
SAMPLES = os.path.join(HERE, "samples")
PASSWORD = "123456"


class Session:
    def __init__(self, login):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.req("GET", "/api/auth/me", ok=(200, 401))
        self.req("POST", "/api/auth/login", login)

    def xsrf(self):
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return urllib.parse.unquote(c.value)
        return ""

    def req(self, method, path, body=None, ok=(200,), files=None, fields=None):
        headers = {"X-XSRF-TOKEN": self.xsrf()}
        data = None
        if files is not None or fields is not None:
            boundary = uuid.uuid4().hex
            buf = io.BytesIO()
            for k, v in (fields or {}).items():
                buf.write(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode("utf-8"))
            for k, (name, content) in (files or {}).items():
                buf.write(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{name}\"\r\n"
                          f"Content-Type: application/octet-stream\r\n\r\n".encode("utf-8"))
                buf.write(content)
                buf.write(b"\r\n")
            buf.write(f"--{boundary}--\r\n".encode())
            data = buf.getvalue()
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        r = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.op.open(r) as resp:
                text = resp.read().decode("utf-8")
                code = resp.status
        except urllib.error.HTTPError as e:
            text = e.read().decode("utf-8", "ignore")
            code = e.code
        if code not in ok:
            raise SystemExit(f"\n✗ {method} {path} → {code} {text[:300]}")
        try:
            return json.loads(text).get("data")
        except ValueError:
            return None


def step(msg):
    print("•", msg, flush=True)


# ---------------- 示例文件 ----------------
W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'


def docx(good):
    """最小的 Word 文档：标题 + 两段正文。good=True 为按要求排好版的“标准答案”。"""
    title_rpr = ('<w:rPr><w:rFonts w:ascii="SimHei" w:hAnsi="SimHei" w:eastAsia="黑体"/><w:b/><w:sz w:val="32"/></w:rPr>' if good
                 else '<w:rPr><w:sz w:val="21"/></w:rPr>')
    jc = '<w:jc w:val="center"/>' if good else ''
    ind = '<w:ind w:firstLineChars="200" w:firstLine="420"/><w:spacing w:line="360" w:lineRule="auto"/>' if good else ''
    body = (f'<w:p><w:pPr>{jc}</w:pPr><w:r>{title_rpr}<w:t>我的家乡</w:t></w:r></w:p>'
            f'<w:p><w:pPr>{ind}</w:pPr><w:r><w:t>我的家乡在海边，这里四季分明，物产丰富，人们勤劳朴实。</w:t></w:r></w:p>'
            f'<w:p><w:pPr>{ind}</w:pPr><w:r><w:t>每到春天，山上开满了杜鹃花，吸引了很多游客。</w:t></w:r></w:p>')
    sect = '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1800" w:bottom="1440" w:left="1800"/></w:sectPr>'
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                   '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>'
                   '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        z.writestr("_rels/.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr("word/document.xml", f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}><w:body>{body}{sect}</w:body></w:document>')
    return out.getvalue()


def site_zip():
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("我的网站/index.html", '<!doctype html><html><head><meta charset="utf-8"><title>我的家乡</title><link rel="stylesheet" href="css/style.css"></head>'
                   '<body><h1>我的家乡</h1><p>这是我做的第一个网站。</p><p><a href="about.html">关于我</a></p></body></html>')
        z.writestr("我的网站/about.html", '<!doctype html><meta charset="utf-8"><h2>关于我</h2><p>高一(1)班 学生</p><a href="index.html">返回首页</a>')
        z.writestr("我的网站/css/style.css", "body{font-family:sans-serif;background:#f0f7ff;padding:30px}h1{color:#2563eb}")
    return out.getvalue()


def write_samples():
    os.makedirs(SAMPLES, exist_ok=True)
    files = {
        "Word-素材（未排版）.docx": docx(False),
        "Word-标准答案（已排版）.docx": docx(True),
        "个人网站示例.zip": site_zip(),
        "Python-两数之和-正确答案.txt": "a, b = map(int, input().split())\nprint(a + b)\n".encode("utf-8"),
        "Python-两数之和-错误答案.txt": "a, b = map(int, input().split())\nprint(a - b)\n".encode("utf-8"),
    }
    for name, data in files.items():
        with open(os.path.join(SAMPLES, name), "wb") as f:
            f.write(data)
    return files


# ---------------- 演示数据 ----------------
def main():
    print(f"连接 {BASE} …")
    files = write_samples()
    step(f"示例文件已生成到 {SAMPLES}")

    admin = Session({"loginType": "ADMIN", "loginName": "admin", "password": "admin123"})
    years = admin.req("GET", "/api/admin/school-years") or []
    if any(y.get("yearCode") == "2026" for y in years):
        raise SystemExit("演示数据已经存在（已有 2026 级）。如需重来，请先运行 reset-demo.bat。")

    admin.req("PUT", "/api/admin/settings", {"school.name": "演示中学", "site.notice": "欢迎使用 ITtools 演示环境！所有账号初始密码都是 123456。"})
    term = admin.req("POST", "/api/admin/terms", {"yearName": "2026-2027", "termName": "第一学期", "displayOrder": 1})
    admin.req("POST", f"/api/admin/terms/{term}/current", {})
    step("学校名称、登录页公告、当前学期 2026-2027 第一学期")

    year = admin.req("POST", "/api/admin/school-years", {"yearCode": "2026", "label": "2026级", "active": True})["id"]
    c1 = admin.req("POST", "/api/admin/classes", {"schoolYearId": year, "name": "高一(1)班", "displayOrder": 1})["id"]
    c2 = admin.req("POST", "/api/admin/classes", {"schoolYearId": year, "name": "高一(2)班", "displayOrder": 2})["id"]
    step("年级 2026级，班级 高一(1)班、高一(2)班")

    admin.req("POST", "/api/admin/users", {"role": "TEACHER", "name": "王老师", "loginName": "wang", "password": PASSWORD})
    admin.req("POST", "/api/admin/users", {"role": "TEACHER", "name": "李老师", "loginName": "li", "password": PASSWORD})
    names1 = ["张小明", "李华", "王芳", "赵磊", "陈静", "刘洋", "杨帆", "周婷"]
    names2 = ["吴昊", "郑爽", "孙悦", "马超", "朱琳", "胡彬"]
    students = []
    for i, n in enumerate(names1, 1):
        students.append(admin.req("POST", "/api/admin/users", {"role": "STUDENT", "name": n, "studentNo": f"{i:02d}", "xjh": f"S2026010{i}",
                                                                "classId": c1, "enrollYearId": year, "password": PASSWORD}))
    for i, n in enumerate(names2, 1):
        admin.req("POST", "/api/admin/users", {"role": "STUDENT", "name": n, "studentNo": f"{i:02d}", "xjh": f"S2026020{i}",
                                               "classId": c2, "enrollYearId": year, "password": PASSWORD})
    step("教师 王老师(wang)、李老师(li)；学生 高一(1)班 8 人、高一(2)班 6 人（密码均为 123456）")

    wang = Session({"loginType": "TEACHER", "loginName": "wang", "password": PASSWORD})

    # 题库与试卷
    q = []
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "SINGLE", "category": "信息技术基础", "stem": "计算机中存储数据的最小单位是？",
                                                              "options": ["字节", "位(bit)", "字", "KB"], "answer": "B", "score": 5,
                                                              "analysis": "位（bit）是最小单位，8 位组成 1 个字节。"})["id"])
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "MULTI", "category": "信息技术基础", "stem": "下列属于输入设备的有？",
                                                              "options": ["键盘", "显示器", "鼠标", "打印机"], "answer": "AC", "score": 5})["id"])
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "JUDGE", "category": "信息技术基础", "stem": "1 字节等于 8 位。", "answer": "T", "score": 5})["id"])
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "FILL", "category": "Python", "stem": "Python 中用于输出的函数是 ____。",
                                                              "answer": ["print"], "score": 5})["id"])
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "SINGLE", "category": "网络", "stem": "IP 地址 192.168.1.1 属于哪一类地址？",
                                                              "options": ["A 类", "B 类", "C 类", "D 类"], "answer": "C", "score": 5})["id"])
    q.append(wang.req("POST", "/api/teacher/quiz/questions", {"qtype": "JUDGE", "category": "网络", "stem": "在网上可以随意公开同学的照片和电话。", "answer": "F", "score": 5})["id"])
    paper = wang.req("POST", "/api/teacher/quiz/papers", {"name": "信息技术基础小测", "items": [{"questionId": x, "score": 5} for x in q]})["id"]
    hw_paper = wang.req("POST", "/api/teacher/quiz/papers", {"name": "第一单元课后作业", "items": [{"questionId": x, "score": 10} for x in q[:4]]})["id"]
    step("题库 6 道题（单选/多选/判断/填空），试卷“信息技术基础小测”“第一单元课后作业”")

    # Python 题
    py = wang.req("POST", "/api/teacher/python/problems", {
        "title": "两数之和", "description": "<p>输入一行两个整数，用空格隔开，输出它们的和。</p><p>样例输入：<code>1 2</code> 样例输出：<code>3</code></p>",
        "starterCode": "# 读取两个整数并输出和\n", "referenceCode": "a, b = map(int, input().split())\nprint(a + b)\n",
        "timeLimitMs": 2000, "practice": True,
        "cases": [{"name": "样例", "input": "1 2", "expected": "3", "score": 40, "hidden": False},
                  {"name": "负数", "input": "-5 8", "expected": "3", "score": 30, "hidden": True},
                  {"name": "大数", "input": "100000 200000", "expected": "300000", "score": 30, "hidden": True}]})["id"]
    step("Python 题“两数之和”（3 个测试点，已开放课后练习）")

    # 课程 1：综合课堂
    course = wang.req("POST", "/api/teacher/courses", {"name": "第1课 认识信息技术", "category": "信息技术基础", "termId": term, "description": "演示用课程，包含各种课堂环节"})["id"]

    def page(title, ptype, content="", **kw):
        body = {"title": title, "pageType": ptype, "content": content, "studentVisible": True}
        body.update(kw)
        return wang.req("POST", f"/api/teacher/courses/{course}/pages", body)["id"]

    page("课堂导入", "TEXT", "<h3>今天我们学习什么？</h3><p>认识信息与信息技术，了解计算机的组成。</p>")
    hw = page("作品：排版《我的家乡》", "HOMEWORK", "<p>下载素材，完成排版后上传：</p><ol><li>标题设为 <b>黑体、三号、居中</b></li>"
              "<li>正文每段 <b>首行缩进 2 字符</b>，<b>1.5 倍行距</b></li></ol>", score=10)
    wang.req("POST", f"/api/teacher/pages/{hw}/file", files={"file": ("我的家乡-素材.docx", files["Word-素材（未排版）.docx"])})
    wang.req("PUT", f"/api/teacher/homework/pages/{hw}/config", {"allowedExts": "docx", "autoGrade": True, "autoVisibility": "ALL", "practice": True,
                                                                "peerReview": False, "showClassmates": True})
    wang.req("PUT", f"/api/teacher/grading/pages/{hw}/rules", {"rules": [
        {"type": "DOCX_PARA_FONT", "param1": "1", "param2": "黑体", "score": 20, "note": "标题黑体"},
        {"type": "DOCX_PARA_SIZE", "param1": "1", "param2": "三号", "score": 20, "note": "标题三号"},
        {"type": "DOCX_PARA_ALIGN", "param1": "1", "param2": "居中", "score": 20, "note": "标题居中"},
        {"type": "DOCX_PARA_INDENT", "param1": "2", "param2": "2", "score": 20, "note": "正文首行缩进"},
        {"type": "DOCX_PARA_LINE_SPACING", "param1": "2", "param2": "1.5倍", "score": 20, "note": "正文 1.5 倍行距"}]})
    quiz = page("课堂小测", "QUIZ", "<p>完成后立即看到成绩和解析。</p>", refId=paper, score=30)
    wang.req("PUT", f"/api/teacher/quiz/pages/{quiz}/config", {"mode": "INSTANT", "timeLimit": 10, "showAnswer": True})
    page("Python：两数之和", "PYTHON", "<p>在浏览器里调试，提交后服务器自动评测。</p>", refId=py)
    sv = page("课堂调查", "SURVEY", "<p>请如实填写，帮助老师改进教学。</p>")
    wang.req("PUT", f"/api/teacher/activity/pages/{sv}/survey", {"nameMode": "TEACHER", "showResult": True, "items": [
        {"qtype": "JUDGE", "stem": "这节课的内容你听懂了吗？"},
        {"qtype": "SINGLE", "stem": "你觉得这节课的难度", "options": ["太简单", "适中", "有点难", "太难"]},
        {"qtype": "MULTI", "stem": "你喜欢哪些环节", "options": ["老师讲解", "动手操作", "小组讨论", "课堂小测"]},
        {"qtype": "FILL", "stem": "给老师的建议"}]})
    tq = page("随机抽题", "TAKE_QUESTION", "<p>点击按钮抽取你的问题，思考后举手回答。</p>")
    wang.req("PUT", f"/api/teacher/activity/pages/{tq}/questions", {"questions": [
        "什么是信息？举一个生活中的例子。", "计算机由哪些部分组成？", "说说你知道的输入设备和输出设备。", "为什么不能随意在网上公开个人信息？"]})
    page("小组讨论：网络安全", "DISCUSS", "<p>讨论：上网时有哪些安全隐患？各组整理 3 条建议。</p>")
    page("抢答", "ANSWER_FIRST", "<p>老师发起抢答后，最先答对的同学加分！</p>")
    page("Storyline 课件包", "STORYLINE", "<p>（教师可在课程编辑中上传 Storyline/网页课件 zip）</p>")
    page("Scratch 作品", "SCRATCH", "<p>（教师可在课程编辑中上传 .sb3 作品，课堂中直接运行）</p>")
    step("课程“第1课 认识信息技术”：图文/作品(Word 自动评分+课后练习)/小测/Python/调查/抽题/讨论/抢答/课件包/Scratch 共 11 页")

    # 课后作业与测验
    wang.req("POST", "/api/teacher/quiz/tests", {"paperId": hw_paper, "classIds": [c1, c2], "kind": "HOMEWORK", "mode": "AUTO"})
    wang.req("POST", "/api/teacher/quiz/tests", {"paperId": paper, "classIds": [c1], "kind": "TEST", "mode": "CONTROL"})
    step("课后作业“第一单元课后作业”发布给两个班；测验“信息技术基础小测”发布给高一(1)班（控制模式，需老师开考）")

    # 资源、教案、消息
    res = wang.req("POST", "/api/teacher/resources", fields={"title": "网络安全小常识", "category": "课外", "description": "课后阅读，了解上网安全知识"},
                   files={"file": ("网络安全小常识.html", ('<!doctype html><meta charset="utf-8"><h2>网络安全小常识</h2>'
                                                       '<ol><li>不要随意点击陌生链接</li><li>密码不要过于简单</li><li>不随意公开个人信息</li></ol>').encode("utf-8"))})
    wang.req("PUT", f"/api/teacher/resources/{res['id']}/classes", {"classIds": [c1, c2]})
    wang.req("POST", "/api/teacher/lesson-plans", fields={"title": "第1课 认识信息技术 教案", "content": "<h3>教学目标</h3><p>1. 理解信息的概念 2. 认识计算机组成</p>",
                                                           "termId": term},
             files={"doc": ("教学设计.docx", files["Word-标准答案（已排版）.docx"])})
    wang.req("POST", "/api/teacher/messages", {"title": "下节课请带 U 盘", "content": "下节课要拷贝素材，请提前准备。", "classIds": [c1]})
    step("阅读资料“网络安全小常识”（两个班）、教案 1 份、给高一(1)班的系统消息")

    # 学生已有的一些记录：张小明做过指法练习
    s1 = Session({"loginType": "STUDENT_XJH", "xjh": "S20260101", "password": PASSWORD})
    s1.req("POST", "/api/student/typing", {"level": "PRIMARY", "durationSec": 60, "typedChars": 180, "correctChars": 172, "words": 30})
    s2 = Session({"loginType": "STUDENT_XJH", "xjh": "S20260102", "password": PASSWORD})
    s2.req("POST", "/api/student/typing", {"level": "PRIMARY", "durationSec": 60, "typedChars": 150, "correctChars": 145, "words": 26})
    step("指法英雄榜预置 2 条成绩")

    print("\n✓ 演示数据准备完成。打开", BASE, "开始测试（测试步骤见 docs/测试手册.md）。")


if __name__ == "__main__":
    import urllib.parse  # noqa: E402  (Session.xsrf 使用)
    t0 = time.time()
    main()
    print(f"用时 {time.time() - t0:.1f} 秒")
