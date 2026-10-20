# Backend changes needed by the Android app

All are additive; existing web pages and endpoints stay as they are.
Send errors as JSON `{"message": "..."}` (the app shows `message` for 400/409 when it is short).
Customer endpoints use the same JWT as `/api/orders` (`Authorization: Bearer …`).

## 1. `POST /api/auth/register`  (public, JSON)
Request – same fields and validation as the web `/register` form, address nested:
```json
{
  "firstName": "Sam", "lastName": "Jones", "email": "sam@example.com", "password": "Str0ng!pass",
  "phone": "0821234567", "phone2": null, "preferredStore": "Kenridge",
  "address": {
    "googlePlaceId": "ChIJ...", "formattedAddress": "12 Main Rd, ...", "latitude": -33.9, "longitude": 18.4,
    "houseNumber": "12", "street": "Main Rd", "area": "Kenridge", "city": "Durbanville",
    "postalCode": "7550", "province": "Western Cape", "country": "South Africa"
  }
}
```
Response **201** with the same body as `/api/auth/login` (`accessToken`, `expiresIn`, `username`, `roles`) so the customer is signed in straight away.
(An empty 201 also works – the app then logs in with the same email and password.)
Errors: **400** invalid fields / address not a verified Google address / weak password (message = password policy text),
**409** email or phone already registered, **429** rate-limited.
Security config: permit `POST /api/auth/register` without a token (same as login) and keep it behind the login rate limiter.

Sketch (reuses what `CustomerController.handleRegister` already does – not compiled against your code):
```java
@PostMapping("/api/auth/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r) {
    AddressSelection a = r.address();
    if (a == null || !a.isVerifiedGoogleAddress()) return ResponseEntity.badRequest().body(new MessageResponse("Please pick a valid address."));
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    try {
        customerService.registerCustomer(r.firstName(), r.lastName(), email, r.password(), r.phone(), r.phone2(),
                a.houseNumber(), a.street(), a.area(), null, r.preferredStore(), a.postalCode(), a);
    } catch (CustomerRegistrationException ex) {
        HttpStatus s = ex.getReason() == CustomerRegistrationException.Reason.PASSWORD ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(s).body(new MessageResponse(ex.getMessage()));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.login(email, r.password()));   // same as /api/auth/login
}
```
Note: `area` and `postalCode` are required by the web form; the app fills `area` from `city` when Google doesn't return one.

## 2. `GET /api/orders/mine`  (customer role)
The signed-in customer's own orders, newest first (also add `GET /api/orders/{id}` limited to the owner if you want a detail screen).
```json
[{ "id": 123, "status": "PREPARING", "orderType": "delivery", "branchName": "Kenridge",
   "createdAt": "2026-10-18T18:42:10", "total": 189.00,
   "items": [{ "name": "Margherita", "quantity": 2, "detail": "30cm · Thin base" }] }]
```
`total` and `items` are optional; the app shows them when present. Pull-to-refresh in the Orders tab re-reads this endpoint.
Today `GET /api/orders` is admin-only, so "mine" must be declared **before** any `/api/orders/{id}` mapping and be allowed for the customer role.

## 3. `GET /api/me` and `PUT /api/me`  (customer role)
```json
{ "firstName": "Sam", "lastName": "Jones", "email": "sam@example.com", "phone": "0821234567", "phone2": null,
  "preferredStore": "Kenridge", "complexName": "Sunset Villas", "address": { ...same address object as above... } }
```
`PUT` accepts the same shape (plus optional `currentPassword` / `newPassword`) and applies the same validation as `/profile/update`. Returns the saved profile.

## 4. PayFast card / EFT payments
The app never sees the merchant key's passphrase: the server prices the order, signs the PayFast fields, and only
trusts PayFast's **ITN** (server-to-server notification) to mark an order paid. The app's redirect handling is just UX.

Config (application.properties / env – **never in the app**):
```properties
payfast.merchant-id=...          # sandbox: from your sandbox.payfast.co.za dashboard
payfast.merchant-key=...
payfast.passphrase=...           # set the same passphrase under Settings → Developer Settings in PayFast
payfast.process-url=https://sandbox.payfast.co.za/eng/process        # live: https://www.payfast.co.za/eng/process
payfast.validate-url=https://sandbox.payfast.co.za/eng/query/validate # live: https://www.payfast.co.za/eng/query/validate
payfast.return-url=https://your-domain/payfast/return
payfast.cancel-url=https://your-domain/payfast/cancel
payfast.notify-url=https://your-domain/api/payfast/notify   # must be reachable from the internet (use ngrok while developing)
```

### 4a. `POST /api/orders/payfast`  (customer role, `Idempotency-Key` header, same body as `POST /api/orders`)
Creates the order exactly like `POST /api/orders` but with `paymentStatus = PENDING`, and **does not release it to the
kitchen** until it is PAID. Same idempotency key ⇒ return the same order with a freshly signed payment (the customer
tapped Pay again after cancelling). `paymentMethod` (`"payfast"` / `"cash"`) is now also sent on `POST /api/orders`.
Response **200**:
```json
{ "order": { "id": 123, "status": "AWAITING_PAYMENT", "orderType": "delivery", "branchName": "Kenridge" },
  "payment": {
    "processUrl": "https://sandbox.payfast.co.za/eng/process",
    "returnUrl": "https://your-domain/payfast/return", "cancelUrl": "https://your-domain/payfast/cancel",
    "fields": { "merchant_id": "...", "merchant_key": "...", "return_url": "...", "cancel_url": "...", "notify_url": "...",
                "name_first": "Sam", "email_address": "sam@example.com", "m_payment_id": "123",
                "amount": "189.00", "item_name": "Order #123", "signature": "<md5>" } } }
```
`fields` must be in PayFast's documented order (as below) – the app posts them as-is. Put the order id in
`return_url`/`cancel_url` query strings if you like; the app matches on the part before `?`.

```java
// Signing – PayFast's rules: non-empty fields in documented order, URL-encoded (spaces as '+'), then &passphrase=…, MD5.
static String pfEncode(String v) { return URLEncoder.encode(v.trim(), StandardCharsets.UTF_8); }

static String pfParamString(Map<String, String> fields) {
    StringJoiner j = new StringJoiner("&");
    fields.forEach((k, v) -> { if (v != null && !v.isBlank() && !"signature".equals(k)) j.add(k + "=" + pfEncode(v)); });
    return j.toString();
}

static String pfSignature(Map<String, String> fields, String passphrase) {
    String s = pfParamString(fields);
    if (passphrase != null && !passphrase.isBlank()) s += "&passphrase=" + pfEncode(passphrase);
    return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));   // org.springframework.util.DigestUtils
}

Map<String, String> f = new LinkedHashMap<>();          // order matters
f.put("merchant_id", cfg.merchantId());   f.put("merchant_key", cfg.merchantKey());
f.put("return_url", cfg.returnUrl());     f.put("cancel_url", cfg.cancelUrl());   f.put("notify_url", cfg.notifyUrl());
f.put("name_first", customer.firstName()); f.put("name_last", customer.lastName()); f.put("email_address", customer.email());
f.put("m_payment_id", String.valueOf(order.getId()));
f.put("amount", String.format(Locale.ROOT, "%.2f", order.getTotal()));   // server-calculated total, incl. delivery
f.put("item_name", "Order #" + order.getId());
f.put("signature", pfSignature(f, cfg.passphrase()));
```

### 4b. `POST /api/payfast/notify`  (public – PayFast calls it; permit without token and exempt from CSRF)
Form-encoded. Always answer **200** quickly, then only mark the order PAID if **all** checks pass:
1. **Signature** – recompute `pfSignature` over the posted fields *in the order received* (minus `signature`) and compare.
2. **Source** – the request comes from PayFast (`www.payfast.co.za`, `sandbox.payfast.co.za`, `w1w.payfast.co.za`,
   `w2w.payfast.co.za`; resolve them to IPs and compare with the client IP – mind your proxy's `X-Forwarded-For`).
3. **Amount** – `amount_gross` equals the order total (±0.01) and `m_payment_id` is a real order awaiting payment.
4. **Server confirmation** – POST `pfParamString(fields)` back to `payfast.validate-url`; the body must be `VALID`.

Then: `payment_status=COMPLETE` ⇒ `paymentStatus = PAID`, store `pf_payment_id`, release the order to the kitchen.
`CANCELLED` ⇒ `paymentStatus = CANCELLED`. PayFast may resend an ITN, so make this idempotent.
Use `request.getParameterMap()` (Tomcat keeps the posted order) rather than binding to a DTO.

### 4c. `GET /api/orders/{id}/payment`  (customer role, owner only)
`{ "status": "PENDING" | "PAID" | "FAILED" | "CANCELLED" }`. The app polls this for ~15 s after PayFast returns the customer.
Declare it before any `/api/orders/{id}` mapping. Expire orders that stay PENDING (e.g. after 30 minutes).

Until 4a exists the app shows "Card payment isn't switched on yet" and nothing is created; `PAYFAST_ENABLED=false`
in the app's `local.properties` hides the option entirely.

## 5. Order tracking (customer side)
The customer app only **reads**. Whatever the driver app does, it just has to keep the order's status and the driver's
last position up to date on the server; the customer screen re-reads this endpoint every 10 s while it's open.

### `GET /api/orders/{id}/tracking`  (customer role, owner only – declare before any `/api/orders/{id}` mapping)
```json
{ "orderId": 123, "status": "OUT_FOR_DELIVERY", "orderType": "delivery", "etaMinutes": 12,
  "branch":      { "name": "Kenridge", "formattedAddress": "1 Shop St, Durbanville", "latitude": -33.838, "longitude": 18.638 },
  "destination": { "formattedAddress": "12 Main Rd, Durbanville", "latitude": -33.826, "longitude": 18.648 },
  "driver": { "name": "Sipho", "phone": "0821234567", "vehicle": "Red scooter · CA 123-456",
              "latitude": -33.832, "longitude": 18.642, "locationUpdatedAt": "2026-10-19 18:42" } }
```
- `status` – the app understands `PENDING`/`AWAITING_PAYMENT` → *received*, `ACCEPTED`/`PREPARING` → *preparing*,
  `READY_FOR_COLLECTION`/`OUT_FOR_DELIVERY` → *on the way / ready*, `DELIVERED`/`COLLECTED`/`COMPLETED` → *done*,
  `CANCELLED`. Spaces or underscores, any case.
- `branch` – the restaurant's pin (needed on the map for both delivery and pickup).
- `destination` – the delivery address's lat/lng (already stored from the Google address). Omit for pickup.
- `driver` – `null` until a driver is assigned; lat/lng `null` until the driver app has reported a position.
  Only expose the driver to the customer of that order, and only while it's on the way.
- `etaMinutes` – optional; omit when unknown.
- Polling stops once the status is done or cancelled.

### What the driver app needs to provide (suggested – the driver-side owner decides)
- `POST /api/driver/location` `{ "latitude": …, "longitude": … }` every ~10–15 s while on a delivery
  → store as the driver's last position + timestamp (the tracking endpoint reads it).
- `POST /api/driver/orders/{id}/status` `{ "status": "OUT_FOR_DELIVERY" | "DELIVERED" }`.
- Assigning an order to a driver (admin/kitchen screen or driver "accept") sets the `driver` shown above.

## 6. Nice to have
- Include `total` in the `POST /api/orders` response so the app can show the exact price after ordering.
- Expose which default pizza toppings are removable on `GET …/pizzas/{id}` so the app can offer "no onions".
- Specials (`/api/branches/{id}/specials`) – the app doesn't order them yet.
