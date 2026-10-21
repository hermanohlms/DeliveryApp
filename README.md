# DeliveryApp

Native Android customer ordering app for Pete’s Pizza. Built with Java, XML layouts, and Material components.

The ordering flow includes searchable branch menus, grouped item choices, editable cart lines, checkout, order confirmation, and tracking. Included burger toppings have no price label; paid extras are separate. Duplicate topping IDs from the catalog are normalised so included toppings cannot be charged again as extras.

## Build and checks

Use the Android SDK configured in `local.properties` and a Gradle-compatible JDK (the project compiles Java 17 sources).

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
bash gradlew :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests cover grouping, pricing, restoring saved selections, independent edit drafts, and replacing cart lines. The emulator suite covers burgers, combos, pizzas, nested specials, cancellation, recreation, catalog retry, stale cart lines, checkout, confirmation, and tracking. It injects an in-process catalog/order service; no test order or payment is sent to the hosted API. Existing preferences and catalog-cache rows are restored after each UI test.

On Android 12 and newer, UI screenshots are saved in `/sdcard/Download/delivery-ordering-qa`. The flow is checked at the normal emulator size and at 320dp width with 130% text size. The quantity/action bar stacks vertically on narrow displays and with large text.

Cart editor contracts are internal activity extras. Existing API request formats, authentication requirements, branch switching, and payment availability are retained.
