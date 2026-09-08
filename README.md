# BeaconWaypoints

信标传送 ——站在已激活的信标上使用指令注册路径点，右键信标打开箱子 GUI，配置信标或进行传送

## 特性

- 公共 / 私人路径点
- Paper 26.2 
- 箱子 GUI
- 路径点图标选择、分页和删除确认
- SQLite 持久化
- 支持中文、英文语言
- 支持群体传送、拴绳宠物/坐骑传送
- 目标信标失效时自动返航

## 构建

要求：Java 25+

```bash
./gradlew build
```

Windows：

```bat
gradlew.bat build
```

输出：`build/libs/BeaconWaypoints-1.0.0.jar`

## 使用

| 操作                       | 效果               |
| ------------------------ | ---------------- |
| `/waypoint <名字>`         | 站在激活信标上注册公共路径点   |
| `/waypoint <名字> public`  | 注册公共路径点          |
| `/waypoint <名字> private` | 注册私人路径点          |
| `/waypoint reload`       | 管理员重载配置、语言和路径点缓存 |
| 右键已注册信标                  | 打开插件箱子 GUI       |
| 潜行右键已注册信标                | 保留原版信标 GUI       |
| 路径点列表普通左键                | 发起传送             |
| 破坏已注册信标                  | 属主或管理员允许时删除路径点   |

别名：`/wp`、`/waypoints`

## 传送规则

传送开始前会预热 2.5 秒，预热结束时播放发射动画。

- 发起人必须站在起点信标上。
- `disable-group-teleporting: true`：只传送发起人。
- `disable-group-teleporting: false`：传送发起人及起点信标周围 5 格内的其他玩家。
- 拴绳实体会被传送时一并带走，收集范围为玩家周围 5 格。
- 玩家沿起点光柱上升，在顶部切换到目标光柱后下降。
- 目标信标失效时，玩家沿起点光柱返航，失效路径点自动删除。
- 到达目标或返航后不播放额外到达粒子和音效。

`launch-player-height` 控制光柱发射高度，默认值为 `576`。

## 权限

| 权限                      | 默认   | 说明                 |
| ----------------------- | ---- | ------------------ |
| `beaconwaypoints.use`   | true | 使用路径点命令与菜单         |
| `beaconwaypoints.admin` | op   | 重载、拆除任意路径点、管理他人路径点 |

## 配置

配置文件：`src/main/resources/config.yml`

- `language`：`zh` / `en`，默认 `zh`
- `max-public-waypoints` / `max-private-waypoints`：数量上限
- `max-name-length` / `force-alphanumeric-names`：名称规则
- `public-waypoint-menu-rows` / `private-waypoint-menu-rows`：菜单行数，范围 `2–5`
- `launch-player-height`：发射高度
- `disable-group-teleporting`：是否关闭群体传送
- `allow-beacon-break-by-owner`：是否允许属主拆除信标
- `discovery-mode`：是否启用路径点发现模式
- `allow-all-worlds` / `allowed-worlds`：世界白名单
- `waypoint-icons`：图标选择器选项

## 项目结构

```text
com.wykwey.beaconwaypoints
├── BeaconWaypoints          # 插件主类和 Paper Lifecycle 命令注册
├── command/                 # Brigadier 命令适配
├── config/                  # 配置和语言管理
├── gui/                     # InventoryHolder 箱子式 GUI
├── listener/                # 信标、库存、世界、传送事件
└── waypoint/                # 路径点模型、存储、领域服务和传送状态机
```
