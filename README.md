# Portal
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal?ref=badge_shield)


秋夜长，殊未央，月明白露澄清光，层城绮阁遥相望。

QQ交流群：599533037（搜索不到请扫描下方二维码）
Telegram: https://t.me/portal_fuqiuluo

![QRCODE](img/qrcodemini.png)

The virtual positioning module based on LSPosed only provides Hook system services to achieve virtual positioning, and cannot be integrated into the APP.

The purpose of this application is to help developers debug the simulation tool of the location information program, and the application will automatically create features once it is installed and launched。

> [!note]
>
> 中文地区特供：
> 
> 1. 本项目根据Apache 2.0许可证开放，可用于任何符合法律的目的，包括商业和非商业用途。我们特别鼓励将其用于学习和研究。使用者应遵守相关法律法规，禁止用于任何违法行为。
> 2. 根据Apache 2.0许可证，您可以自由修改、分发本代码及创建衍生作品，但需遵守以下条件：
>     * 保留原始版权声明和附带的NOTICE文件
>     * 提供Apache 2.0许可证副本
>     * 说明您所做的重大修改
> 3. 使用者需承诺遵守相关法律法规，因使用本软件导致的任何后果由使用者自行承担，与本项目开发者无关。
> 4. 开发者保留在使用者违反Apache 2.0许可证条款时追究法律责任的权利。

# Warning

- 如发现任何人利用Portal进行违法活动，请收集证据并向有关部门举报。
- 使用者应遵守所有适用的法律法规。任何企业/组织/个人对因违法使用Portal而产生的后果需自行承担责任。
- Portal开发者对任何因使用本软件而导致的法律纠纷不承担责任。
- 若有企业/组织/个人因使用Portal遇到技术问题导致损失或业务中断，Portal开发团队将在合理范围内提供技术支持和协助。
- Portal开发团队保留对本软件技术实现细节的最终解释权。

## How to detect **Portal**?

- **Portal** will create a notification when it is running, and you can check the notification to see if **Portal** is running.
- **Portal** will add extra to the `Location`, you can check it to see if **Portal** is running.

```kotlin
if (location.extras == null) {
    location.extras = Bundle()
}
location.extras?.putBoolean("portal.enable", true)
location.extras?.putBoolean("is_mock", true)
```

# Features

- [x] **Portal** will create a notification when it is running.
- [x] **Portal** will add extra to the `Location`.
- [x] **Portal** will mock in any case.
- [ ] **Portal** will mock the gps status.
- [ ] **Portal** will mock the cell info.
- [ ] **Portal** will mock the wifi info.
- [x] **Portal** will mock the sensor info.
- [x] **Portal** can move position by rocker.
- [x] **Portal** can set the speed in the settings.
- [x] **Portal** can set the altitude in the settings.
- [x] **Portal** can set the accuracy in the settings.
- [x] **Portal** will change the bearing when moving.

# Thanks

- [GoGoGo](https://github.com/ZCShou/GoGoGo)
- [Baidu Map SDK](https://lbsyun.baidu.com/faq/api?title=androidsdk)


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal?ref=badge_large)
