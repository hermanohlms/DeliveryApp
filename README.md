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

## Driver accounts

The same APK routes `ROLE_USER` accounts to customer ordering, `ROLE_DRIVER` accounts to the driver dashboard, and manager/admin accounts to a staff-web-app message. Existing installations with no saved role require one fresh sign-in. A manager must provision the driver's active profile and assigned branch in web dispatch; public registration still creates customers only.

Driver sign-in starts or resumes a server-recorded shift. Claim next takes the oldest unassigned accepted delivery in that branch, including orders still being prepared. Pickup waits for kitchen completion. After marking a delivery delivered, the driver can claim again immediately. End shift & sign out waits for server-confirmed clock-out and blocks unfinished assignments. Clocked-out shifts await the existing manager cash-up before a new shift starts.

The dashboard refreshes every 10 seconds while visible and when returning from Maps or the dialler. Shift changes require a connection; uncertain responses are reconciled with the server before retrying. Closing the app or session expiry does not clock out the driver. This version requests no location permissions and does not send live GPS positions.

Driver API DTOs mirror the existing `/api/driver` endpoints. The server serializes per-driver shift and dispatch changes with transaction advisory locks, keeps order-level FIFO locking, and preserves database role/branch restrictions. Deploy that compatible server update before distributing this APK.

Driver unit and fixture UI tests cover role routing, starting/resuming shifts, kitchen readiness, delivery confirmation/cancellation, offline recovery, expiry, and a lost clock-out response. Fixture traffic never creates production shifts or deliveries. Screenshots are saved alongside the existing ordering QA captures.
