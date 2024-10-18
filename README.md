# Portal

秋夜长，殊未央，月明白露澄清光，层城绮阁遥相望。

The virtual positioning module based on LSPosed only provides Hook system services to achieve virtual positioning, and cannot be integrated into the APP.

The purpose of this application is to help developers debug the simulation tool of the location information program, and the application will automatically create features once it is installed and launched。

> [!note]
>
> 中文地区特供：
> 
> 本项目仅供学习交流使用，不得用于商业用途或非法操作，否则后果自负。
> 禁止任何目的修改**Portal**的名称/包名为避免不法分子利用。
> 
> 本项目为在实验室“人工智能+大数据”智能物流车提供模拟位置服务，不得用于任何违法行为。

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
- [ ] **Portal** can set the speed in the settings.
- [ ] **Portal** can set the altitude in the settings.
- [ ] **Portal** can set the accuracy in the settings.
- [ ] **Portal** will change the bearing when moving.

# Thanks

- [GoGoGo](https://github.com/ZCShou/GoGoGo)
- [Baidu Map SDK](https://lbsyun.baidu.com/faq/api?title=androidsdk)
