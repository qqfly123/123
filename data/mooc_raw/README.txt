=========================================
  MOOC 原始数据存放目录
=========================================

请将你从MOOC平台导出的CSV文件放到此文件夹中。
系统会自动检测CSV文件中的列名并转换为系统所需格式。

支持的数据类型（按文件自动识别）：
------------------------------------------

1. 用户/学习者数据（包含以下任一列名即可识别）：
   - 中文列名: 用户ID, 用户名, 姓名, 昵称, 注册时间, 注册日期
   - 英文列名: user_id, username, name, nickname, register_time, registration_date

2. 课程数据（包含以下任一列名即可识别）：
   - 中文列名: 课程ID, 课程名, 课程名称, 分类, 类别, 难度
   - 英文列名: course_id, course_name, category, difficulty

3. 学习记录/成绩数据（包含以下任一列名即可识别）：
   - 中文列名: 用户ID+课程ID, 成绩, 分数, 得分, 完成时间, 学习时长
   - 英文列名: user_id+course_id, grade, score, completion_date, study_hours

示例文件格式：
------------------------------------------

用户数据 (users.csv):
  user_id,username,register_time
  U001,张三,2023-01-15
  U002,李四,2023-03-20

课程数据 (courses.csv):
  course_id,course_name,category,difficulty
  C001,Python程序设计,编程语言,1
  C002,数据结构,计算机基础,2

成绩数据 (grades.csv):
  user_id,course_id,score,completion_date,study_hours
  U001,C001,92,2023-06-01,40
  U001,C002,85,2023-08-15,55

也支持合并在一个文件中的格式：
  user_id,username,course_name,score,completion_date
  U001,张三,Python程序设计,92,2023-06-01

导入方式：
------------------------------------------
1. 将CSV文件放入此文件夹
2. 访问系统首页，点击侧边栏"📥 数据导入"
3. 点击"检测并导入"按钮
4. 或调用 API: POST /api/import/mooc
