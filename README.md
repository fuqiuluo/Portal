# Portal
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Ffuqiuluo%2FPortal?ref=badge_shield)


秋夜长，殊未央，月明白露澄清光，层城绮阁遥相望。

QQ交流群：599533037

The virtual positioning module based on LSPosed only provides Hook system services to achieve virtual positioning, and cannot be integrated into the APP.

The purpose of this application is to help developers debug the simulation tool of the location information program, and the application will automatically create features once it is installed and launched。

> [!note]
>
> 中文地区特供：
> 
> 1. 本项目遵循[Apache-2.0 license]，仅限于非商业用途的学习、研究目的，禁止用于任何违法行为。
> 2. 未经书面授权，禁止修改、反向工程、重新包装或分发本项目的名称、代码及衍生作品。
> 3. 使用者需承诺遵守相关法律法规，因滥用导致的后果由行为人自行承担，与本项目开发者无关。
> 4. 开发者保留对违反本协议的行为追究法律责任的权利。

# Warning

- 一旦有任何人使用**Portal**进行任何违法行为，请立即收集证据举报。
- 禁止以违法目的使用**Portal**/分发**Portal**，否则后果自负。 任何企业/组织/个人不得以任何形式使用**Portal**进行违法行为，否则后果自负。
- 如有企业/组织/个人因为**Portal**导致的任何法律纠纷，**Portal**开发者概不负责。
- 若有企业/组织/个人因为**Portal**导致出现任何损失，业务中断，**Portal**将最大程度协助您的调查。
- **Portal**开发者保留对**Portal**的最终解释权。

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
