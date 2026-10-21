package com.example.deliveryapp;

import android.app.Activity;
import android.content.*;
import android.database.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.deliveryapp.data.*;
import com.example.deliveryapp.ui.*;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import okhttp3.Request;
import okio.Timeout;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.*;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Exercises real screens against an in-process catalog. No order or payment reaches a server. */
@RunWith(AndroidJUnit4.class)
public class OrderingJourneyTest {
    private final Gson gson = new Gson();
    private Api originalApi;
    private DriverModels.Dashboard driverState;
    private int driverClockIns, driverClockOuts;
    private boolean driverOffline, loseClockOutReply, rejectClockOut;
    private String loginRole="ROLE_USER";
    private Map<String, ?> savedPreferences;
    private Map<Integer,List<Models.MenuItem>> savedMemory;
    private final Map<String,List<ContentValues>> savedTables = new HashMap<>();
    private SharedPreferences preferences;
    private MenuCacheDb cache;
    private Field apiField, memoryField;
    private ActivityScenario<?> scenario;
    private volatile boolean failMenu;
    private int submitted;
    private String submittedKey;
    private List<Models.MenuItem> menu;
    private Models.Special special;
    private Models.Profile serverProfile;
    private boolean deferProfile;
    private volatile Runnable pendingProfileReply;

    @Before public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("delivery_app", Context.MODE_PRIVATE);
        savedPreferences = new HashMap<>(preferences.getAll());
        apiField = Repo.class.getDeclaredField("api"); apiField.setAccessible(true);
        originalApi = (Api) apiField.get(Repo.get());
        memoryField = Repo.class.getDeclaredField("menuCache"); memoryField.setAccessible(true);
        savedMemory = new HashMap<>((Map<Integer,List<Models.MenuItem>>) memoryField.get(Repo.get()));
        Field field = Repo.class.getDeclaredField("catalogDb"); field.setAccessible(true); cache = (MenuCacheDb)field.get(Repo.get());
        for (String table : List.of("branch_menu","catalog_cache")) {
            List<ContentValues> rows = new ArrayList<>();
            try (Cursor cursor = cache.getReadableDatabase().query(table,null,null,null,null,null,null)) {
                while (cursor.moveToNext()) { ContentValues row = new ContentValues(); DatabaseUtils.cursorRowToContentValues(cursor,row);rows.add(row); }
            }
            savedTables.put(table,rows);
        }
        for (String table : savedTables.keySet()) cache.getWritableDatabase().delete(table,null,null);
        menu = new ArrayList<>(DemoData.menuItems());
        Models.MenuItem combo = gson.fromJson(gson.toJson(menu.get(0)),Models.MenuItem.class);
        combo.menuItemId=103;combo.name="Classic Beef Burger Combo";combo.price=100.0;menu.add(combo);
        // Reproduce the hosted API's included/extra duplicate for the same topping ID.
        Models.BurgerTopping duplicate = gson.fromJson(gson.toJson(menu.get(0).burgerToppings.get(4)),Models.BurgerTopping.class);
        duplicate.defaultSelected=false;duplicate.price=14.0;
        menu.get(0).burgerToppings=new ArrayList<>(menu.get(0).burgerToppings);menu.get(0).burgerToppings.add(duplicate);
        special = special();
        Api fake = (Api)Proxy.newProxyInstance(Api.class.getClassLoader(),new Class[]{Api.class},(proxy,method,args) ->
                new FixtureCall(response(method.getName(),args),deferProfile && "me".equals(method.getName())));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { apiField.set(Repo.get(),fake); ((Map<?,?>)memoryField.get(Repo.get())).clear(); } catch(Exception e) { throw new RuntimeException(e); }
            Cart.get().clear();
            Models.AuthResponse auth = new Models.AuthResponse();auth.accessToken="ui-test-only";auth.username="ui-test@example.test";auth.expiresIn=3600;auth.roles=List.of("ROLE_USER");
            Prefs.get().saveSession(auth,auth.username); Prefs.get().setBranch(Branch.fromId(1)); Prefs.get().saveProfile(DemoData.profile());
        });
    }

    @After public void restore() throws Exception {
        if (scenario != null) scenario.close();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity a : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) a.finish();
            Cart.get().clear();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        if (originalApi != null) apiField.set(Repo.get(),originalApi);
        if (savedMemory != null) { Map<Integer,List<Models.MenuItem>> memory=(Map<Integer,List<Models.MenuItem>>)memoryField.get(Repo.get());memory.clear();memory.putAll(savedMemory); }
        if (cache != null) {
            SQLiteDatabase db=cache.getWritableDatabase();db.beginTransaction();
            try { for (Map.Entry<String,List<ContentValues>> table:savedTables.entrySet()) {
                db.delete(table.getKey(),null,null);for(ContentValues row:table.getValue())db.insertOrThrow(table.getKey(),null,row);
            } db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        }
        if (savedPreferences != null) {
            SharedPreferences.Editor edit=preferences.edit().clear();
            for (Map.Entry<String,?> e:savedPreferences.entrySet()) {
                Object v=e.getValue();String k=e.getKey();
                if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
                else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,(Set<String>)v);
            } edit.commit();
        }
    }

    private void prepareDriver(String shiftState) {
        driverState=new DriverModels.Dashboard(); driverState.profile=new DriverModels.Profile();
        driverState.profile.id=44;driverState.profile.branchId=1;driverState.profile.firstName="Test";
        driverState.profile.lastName="Driver";driverState.profile.email="driver@example.test";driverState.profile.active=true;
        if(shiftState!=null) {
            driverState.shift=new DriverModels.Shift(); driverState.shift.id=99;driverState.shift.state=shiftState;
            driverState.shift.clockedInAt=java.time.OffsetDateTime.now().minusHours(1).toString();
            driverState.shift.hourlyRate=60;driverState.shift.perDeliveryRate=10;
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Models.AuthResponse auth=new Models.AuthResponse();auth.username="driver@example.test";
            auth.accessToken="fixture-driver-only";auth.expiresIn=3600;auth.roles=List.of("ROLE_DRIVER");
            Prefs.get().saveSession(auth,auth.username);
        });
    }
    @Test public void driverLoginStartsShiftAndDeliveryJourneyRecoversLostClockOut() throws Exception {
        prepareDriver(null);loginRole="ROLE_DRIVER";
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Repo.get().logout());
        scenario=ActivityScenario.launch(LoginActivity.class);
        onView(withId(R.id.etEmail)).perform(replaceText("driver@example.test"));
        onView(withId(R.id.etPassword)).perform(replaceText("fixture-only"),closeSoftKeyboard());
        onView(withId(R.id.btnLogin)).perform(click());
        onView(withId(R.id.tvDriverName)).check(matches(withText("Test Driver")));
        assertEquals(1,driverClockIns);recreateCurrent();
        onView(withId(R.id.btnDriverClaim)).check(matches(isEnabled()));assertEquals(1,driverClockIns);
        capture("driver-dashboard");
        onView(withId(R.id.btnDriverClaim)).perform(click());
        onView(withText("Mark picked up")).perform(scrollTo()).check(matches(not(isEnabled())));
        onView(withId(R.id.btnDriverEnd)).perform(scrollTo()).check(matches(not(isEnabled())));
        verifyDriverExternalActions();
        driverState.deliveries.get(0).order.status="Completed";
        onView(withId(R.id.btnDriverRetry)).perform(scrollTo(),click());
        onView(withText("Mark picked up")).perform(scrollTo(),click());
        capture("driver-picked-up");
        onView(withText("Mark delivered")).perform(scrollTo(),click());
        onView(withText("Cancel")).perform(click());assertEquals("PICKED_UP",driverState.deliveries.get(0).state);
        onView(withText("Mark delivered")).perform(click());
        onView(allOf(withText("Mark delivered"),isAssignableFrom(android.widget.Button.class),not(isDescendantOfA(withId(R.id.driverActive)))))
                .perform(click());
        onView(withId(R.id.btnDriverClaim)).perform(scrollTo()).check(matches(isEnabled()));
        assertEquals("DELIVERED",driverState.deliveries.get(0).state);
        loseClockOutReply=true;
        onView(withId(R.id.btnDriverEnd)).perform(scrollTo(),click());
        onView(withText("End shift & sign out")).perform(click());
        onView(withId(R.id.btnViewMenu)).check(matches(isDisplayed()));
        assertEquals(1,driverClockOuts);assertFalse(Prefs.get().isLoggedIn());
    }
    private void verifyDriverExternalActions() {
        android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        List<android.content.Intent> opened=new ArrayList<>();
        android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor() {
            @Override public android.app.Instrumentation.ActivityResult onStartActivity(android.content.Intent intent) {
                if(android.content.Intent.ACTION_DIAL.equals(intent.getAction()) || android.content.Intent.ACTION_VIEW.equals(intent.getAction())) {
                    opened.add(intent);return new android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                }
                return null;
            }
        };
        instrument.addMonitor(monitor);
        try {
            onView(withText("Call 0800000000")).perform(scrollTo(),click());
            onView(withText("Navigate")).perform(scrollTo(),click());
            assertEquals(2,opened.size());assertEquals("tel:0800000000",opened.get(0).getDataString());
            assertEquals("12 Test Road, Cape Town",opened.get(1).getData().getQueryParameter("destination"));
        } finally { instrument.removeMonitor(monitor); }
    }
    @Test public void failedClockOutKeepsDriverSignedInUntilServerConfirmsRetry() {
        prepareDriver("ACTIVE");rejectClockOut=true;scenario=ActivityScenario.launch(DriverActivity.class);
        onView(withId(R.id.btnDriverEnd)).perform(scrollTo(),click());
        onView(withText("End shift & sign out")).perform(click());
        onView(withId(R.id.tvDriverError)).perform(scrollTo()).check(matches(isDisplayed()));
        assertTrue(Prefs.get().isLoggedIn());assertEquals("ACTIVE",driverState.shift.state);
        rejectClockOut=false;
        onView(withId(R.id.btnDriverEnd)).perform(scrollTo(),click());
        onView(withText("End shift & sign out")).perform(click());
        onView(withId(R.id.btnViewMenu)).check(matches(isDisplayed()));assertEquals(2,driverClockOuts);
    }
    @Test public void driverResumeDoesNotRestartClockedOutShiftAndCustomerCannotOpenDriverArea() {
        prepareDriver("CLOCKED_OUT");scenario=ActivityScenario.launch(LandingActivity.class);
        onView(withId(R.id.tvDriverShift)).check(matches(withText("Awaiting manager cash-up")));
        onView(withId(R.id.btnDriverClaim)).check(matches(not(isEnabled())));assertEquals(0,driverClockIns);
        scenario.close();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Models.AuthResponse auth=DemoData.auth();auth.accessToken="fixture-customer-only";
            Prefs.get().saveSession(auth,auth.username);
        });
        scenario=ActivityScenario.launch(DriverActivity.class);
        onView(withId(R.id.btnOrder)).check(matches(isDisplayed()));
    }
    @Test public void driverOfflineDisablesChangesAndRestoresExistingShift() throws Exception {
        prepareDriver("ACTIVE");driverOffline=true;scenario=ActivityScenario.launch(DriverActivity.class);
        onView(withId(R.id.tvDriverError)).check(matches(isDisplayed()));
        onView(withId(R.id.btnDriverClaim)).check(matches(not(isEnabled())));
        driverOffline=false;onView(withId(R.id.btnDriverRetry)).perform(click());
        onView(withId(R.id.btnDriverClaim)).check(matches(isEnabled()));assertEquals(0,driverClockIns);
        capture("driver-reconnected");
        recreateCurrent();onView(withId(R.id.btnDriverClaim)).check(matches(isEnabled()));assertEquals(0,driverClockIns);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> preferences.edit().putLong("expires",1).commit());
        recreateCurrent();onView(withId(R.id.btnViewMenu)).check(matches(isDisplayed()));assertEquals(0,driverClockOuts);
    }
    @Test public void managerRoleOpensStaffSiteMessageEvenWithCustomerAuthority() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Models.AuthResponse a=DemoData.auth();a.accessToken="fixture-manager-only";
            a.roles=List.of("ROLE_USER","ROLE_ADMIN","ROLE_SUPER_ADMIN");Prefs.get().saveSession(a,a.username);
        });
        scenario=ActivityScenario.launch(LandingActivity.class);
        onView(withText("Manager and admin accounts use the staff web app.")).check(matches(isDisplayed()));
    }

    @Test public void burgerGroupingEditCancelSaveAndRotation() throws Exception {
        scenario=ActivityScenario.launch(MainActivity.class);
        onView(allOf(withText("Burgers"),isAssignableFrom(Chip.class))).perform(scrollTo(),click());
        onView(withText("Classic Beef Burger")).perform(click());
        onView(withText("Tomato")).perform(scrollTo(),click());
        capture("burger-defaults");
        onView(withText("Bacon")).perform(scrollTo(),click());
        capture("burger-extras");
        onView(withText("Chips")).perform(scrollTo(),click());
        onView(withId(R.id.etNotes)).perform(scrollTo(),replaceText("No salt"),closeSoftKeyboard());
        onView(withId(R.id.btnPlus)).perform(click());
        capture("burger-customisation");
        onView(withId(R.id.btnAdd)).perform(click());
        onView(withId(R.id.btnViewCart)).perform(click());
        assertEquals(1,Cart.get().lines().size());
        Cart.Line original=Cart.get().lines().get(0);assertEquals(81,original.unitPrice,.001);assertEquals(2,original.quantity);
        assertEquals("No salt",original.request.notes);
        onView(withId(R.id.btnEdit)).perform(click());
        onView(withText("Tomato")).perform(scrollTo()).check(matches(isNotChecked()));
        onView(withText("Bacon")).perform(scrollTo()).check(matches(isChecked()));
        onView(withText("Fried egg")).perform(scrollTo(),click());
        recreateCurrent();
        onView(withText("Fried egg")).perform(scrollTo()).check(matches(isChecked()));
        onView(withId(R.id.etNotes)).perform(scrollTo()).check(matches(withText("No salt")));
        pressBack();
        assertSame(original,Cart.get().find(original.id));assertEquals(81,Cart.get().find(original.id).unitPrice,.001);
        onView(withId(R.id.btnEdit)).perform(click());
        onView(withText("Fried egg")).perform(scrollTo(),click());
        onView(withId(R.id.btnAdd)).perform(click());
        assertEquals(1,Cart.get().lines().size());assertEquals(original.id,Cart.get().lines().get(0).id);
        assertEquals(89,Cart.get().find(original.id).unitPrice,.001);assertEquals(2,Cart.get().find(original.id).quantity);
        capture("cart-edited");
    }

    @Test public void comboCanBeChangedAndUpdatedInPlace() {
        scenario=ActivityScenario.launch(MainActivity.class);
        onView(allOf(withText("Burgers"),isAssignableFrom(Chip.class))).perform(scrollTo(),click());
        onView(withText("Classic Beef Burger")).perform(click());
        onView(withText("Burger combo")).perform(scrollTo(),click());
        onView(withText("Chips")).perform(scrollTo(),click());onView(withId(R.id.btnAdd)).perform(click());
        onView(withId(R.id.btnViewCart)).perform(click());onView(withId(R.id.btnEdit)).perform(click());
        onView(withText("Individual burger")).perform(scrollTo(),click());
        onView(withText("Chips")).perform(scrollTo(),click());onView(withId(R.id.btnAdd)).perform(click());
        assertEquals(1,Cart.get().lines().size());assertEquals(101,(int)Cart.get().lines().get(0).request.menuItemId);
    }

    @Test public void pizzaEditKeepsSizeBaseExtrasNotesAndQuantity() {
        scenario=ActivityScenario.launch(MainActivity.class);
        onView(withText("Margherita")).perform(click());
        onView(withText("Stuffed crust")).perform(scrollTo(),click());
        onView(withText("Mushrooms")).perform(scrollTo(),click());
        onView(withId(R.id.etNotes)).perform(scrollTo(),replaceText("Crispy"),closeSoftKeyboard());
        onView(withId(R.id.btnPlus)).perform(click());onView(withId(R.id.btnAdd)).perform(click());
        onView(withId(R.id.btnViewCart)).perform(click());Cart.Line original=Cart.get().lines().get(0);
        onView(withId(R.id.btnEdit)).perform(click());
        onView(withText("Mushrooms")).perform(scrollTo()).check(matches(isChecked()));
        onView(withText("Stuffed crust")).perform(scrollTo()).check(matches(isChecked()));
        onView(withId(R.id.etNotes)).perform(scrollTo()).check(matches(withText("Crispy")));
        onView(withId(R.id.tvQty)).check(matches(withText("2")));onView(withId(R.id.btnAdd)).perform(click());
        assertEquals(1,Cart.get().lines().size());assertEquals(original.id,Cart.get().lines().get(0).id);
        assertEquals(original.request.sizeCm,Cart.get().lines().get(0).request.sizeCm);
    }

    @Test public void specialNestedCustomisationSurvivesCartEditAndRotation() throws Exception {
        scenario=ActivityScenario.launch(MainActivity.class);
        onView(allOf(withText("Specials"),isAssignableFrom(Chip.class))).perform(scrollTo(),click());
        onView(withText("Pizza night")).perform(click());
        onView(withText("Customize Margherita")).perform(scrollTo(),click());
        onView(withText("Mushrooms")).perform(scrollTo(),click());
        onView(withId(R.id.etNotes)).perform(scrollTo(),replaceText("Crispy"),closeSoftKeyboard());
        onView(withId(R.id.btnAdd)).perform(click());
        onView(withId(R.id.btnPlus)).perform(click());onView(withId(R.id.btnAdd)).perform(click());
        onView(withId(R.id.btnViewCart)).perform(click());Cart.Line original=Cart.get().lines().get(0);
        onView(withId(R.id.btnEdit)).perform(click());recreateCurrent();
        onView(withText("Customize Margherita")).perform(scrollTo(),click());
        onView(withText("Mushrooms")).perform(scrollTo()).check(matches(isChecked()));
        onView(withId(R.id.etNotes)).perform(scrollTo()).check(matches(withText("Crispy")));
        onView(withId(R.id.btnAdd)).perform(click());onView(withId(R.id.btnAdd)).perform(click());
        assertEquals(1,Cart.get().lines().size());assertEquals(original.id,Cart.get().lines().get(0).id);
        assertEquals(2,Cart.get().lines().get(0).quantity);assertEquals(162,Cart.get().lines().get(0).unitPrice,.001);capture("special-cart");
    }

    @Test public void checkoutConfirmationAndTrackingUseFixtureOrdersOnly() throws Exception {
        Models.MenuItem burger=menu.get(0);MenuCustomization selection=new MenuCustomization(burger,menu);
        selection.toggle(burger.modifierGroups.get(0),2001);
        Cart.get().add(new Cart.Line(burger.name,selection.summary(),selection.unitPrice(),1,selection.request("No salt")));
        scenario=ActivityScenario.launch(CartActivity.class);
        onView(withId(R.id.btnCheckout)).perform(click());
        onView(withId(R.id.chipPickup)).perform(scrollTo(),click());
        onView(withId(R.id.tvCheckoutSubtotal)).check(matches(withText("R69.00")));
        onView(withId(R.id.etPhone)).perform(scrollTo(),replaceText("0839990000"),closeSoftKeyboard());
        capture("checkout");onView(withId(R.id.btnPlace)).perform(click());
        onView(withId(R.id.tvOrderId)).check(matches(withText("Order #777")));
        assertEquals("0821234567",Prefs.get().getProfile().phone);
        assertEquals(1,submitted);assertNotNull(submittedKey);assertTrue(Cart.get().isEmpty());capture("confirmation");
        onView(withId(R.id.btnTrack)).perform(scrollTo(),click());
        onView(withId(R.id.tvStatus)).check(matches(withText("Preparing")));capture("tracking");
    }

    @Test public void checkoutLoadsAccountDetailsWithoutOverwritingAnEditedField() {
        serverProfile=DemoData.profile();
        serverProfile.firstName="Server";
        serverProfile.lastName="Customer";
        serverProfile.phone="0835551234";
        serverProfile.address=serverProfile.address.copy();
        serverProfile.address.formattedAddress="20 Kenridge Ave, Cape Town";
        deferProfile=true;
        scenario=ActivityScenario.launch(CheckoutActivity.class);
        onView(withId(R.id.etName)).check(matches(withText("Demo Customer")));
        onView(withId(R.id.etName)).perform(scrollTo(),replaceText("Order Guest"),closeSoftKeyboard());
        assertNotNull(pendingProfileReply);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(pendingProfileReply);
        onView(withId(R.id.etName)).check(matches(withText("Order Guest")));
        onView(withId(R.id.etPhone)).check(matches(withText("0835551234")));
        onView(withId(R.id.addrInput)).check(matches(withText("20 Kenridge Ave, Cape Town")));
        assertEquals("Server",Prefs.get().getProfile().firstName);
    }

    @Test public void failedCatalogShowsRetryAndRemovedCartLineCannotBeSaved() throws Exception {
        failMenu=true;
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        scenario=ActivityScenario.launch(new Intent(context,ItemDetailActivity.class).putExtra(ItemDetailActivity.EXTRA_ID,101));
        onView(withId(R.id.tvError)).check(matches(withText(containsString("reach the server"))));capture("catalog-error");
        failMenu=false;onView(withId(R.id.btnRetry)).perform(click());
        onView(withText("Tomato")).perform(scrollTo()).check(matches(isChecked()));
        scenario.close();scenario=ActivityScenario.launch(new Intent(context,ItemDetailActivity.class)
                .putExtra(ItemDetailActivity.EXTRA_ID,101).putExtra(CartEditor.EXTRA_LINE_ID,"removed-line"));
        onView(withId(R.id.tvError)).check(matches(withText(containsString("no longer in your cart"))));
        onView(withId(R.id.btnAdd)).check(matches(isNotEnabled()));assertTrue(Cart.get().isEmpty());
    }

    private void recreateCurrent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))activity.recreate();
        }); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void capture(String name) throws Exception {
        // Let layout/IME animations settle before recording the actual visible result.
        SystemClock.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(bitmap==null)return;
        if(Build.VERSION.SDK_INT >= 31) {
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("mkdir -p /sdcard/Download/delivery-ordering-qa"))){while(in.read()!=-1) { }}
            ParcelFileDescriptor[] pipe=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommandRw(
                    "dd of=/sdcard/Download/delivery-ordering-qa/"+name+".png");
            try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){while(in.read()!=-1) { }}
        }
        bitmap.recycle();
    }
    private Models.Special special() {
        Models.Special s=new Models.Special();s.id=50;s.name="Pizza night";s.description="Build your pizza bundle";s.price=150;s.availableToday=true;
        Models.SpecialComponent c=new Models.SpecialComponent();c.id=60;c.label="Pizza";c.productType="PIZZA";c.quantity=1;c.allowCustomization=true;c.allowRepeats=true;c.sizeCm=30;
        Models.SpecialOption o=new Models.SpecialOption();o.id=1;o.name="Margherita";c.options=List.of(o);s.components=List.of(c);return s;
    }
    private Models.QuoteResponse quote(int id,Models.QuoteRequest request) {
        Models.PizzaDetail detail=DemoData.pizzaDetail(id,request.sizeCm);double total=detail.basePriceForSelectedSize;
        for(Models.PizzaBaseOption b:detail.baseOptions)if(request.pizzaBaseOptionId!=null && b.pizzaBaseOptionId==request.pizzaBaseOptionId)total+=b.extraPrice;
        Set<Integer> defaults=new HashSet<>();for(Models.ToppingItem t:detail.defaultToppings)defaults.add(t.toppingId);
        for(Models.ToppingPrice p:detail.extraPrices)if(request.selectedToppingIds.contains(p.toppingId) && !defaults.contains(p.toppingId))total+=p.extraPrice;
        Models.QuoteResponse quote=new Models.QuoteResponse();quote.total=total;return quote;
    }
    private Object response(String method,Object[] args) {
        switch(method) {
            case "login":Models.AuthResponse login=DemoData.auth();login.accessToken="fixture-login-only";login.roles=List.of(loginRole);login.username="driver@example.test";return login;
            case "driverDashboard":return driverOffline ? new IOException("Fixture offline") : driverState;
            case "driverClockIn":driverClockIns++;driverState.shift=new DriverModels.Shift();driverState.shift.id=99;
                driverState.shift.state="ACTIVE";driverState.shift.clockedInAt=java.time.OffsetDateTime.now().toString();return driverState.shift;
            case "driverClaim":DriverModels.Delivery delivery=new DriverModels.Delivery();delivery.assignmentId=1;delivery.state="ASSIGNED";
                delivery.order=new DriverModels.Order();delivery.order.id=123;delivery.order.status="Preparing";
                delivery.order.customerName="Fixture Customer";delivery.order.formattedAddress="12 Test Road, Cape Town";
                delivery.order.phone="0800000000";delivery.order.gateAccessCode="fixture";delivery.order.notes="Ring bell";
                driverState.deliveries.add(delivery);return driverState;
            case "driverPickup":driverState.deliveries.get(0).state="PICKED_UP";return driverState;
            case "driverDelivered":driverState.deliveries.get(0).state="DELIVERED";return driverState;
            case "driverClockOut":driverClockOuts++;if(rejectClockOut)return new IOException("Clock-out failed before commit");
                driverState.shift.state="CLOCKED_OUT";
                driverState.shift.clockedOutAt=java.time.OffsetDateTime.now().toString();
                return loseClockOutReply ? new IOException("Reply lost after commit") : driverState.shift;
            case "menuItems":return failMenu ? new IOException("Fixture offline") : menu;
            case "pizzas":return DemoData.pizzas();
            case "pizzaDetail":return DemoData.pizzaDetail((Integer)args[1],(Integer)args[2]);
            case "quote":return quote((Integer)args[1],(Models.QuoteRequest)args[2]);
            case "specials":return List.of(special);
            case "specialQuote":
                Models.SpecialItemRequest bundle=(Models.SpecialItemRequest)args[2];double unit=150;
                for(Models.SpecialSelection choice:bundle.selections)if(choice.item.pizzaId!=null){
                    Models.PizzaDetail detail=DemoData.pizzaDetail(choice.item.pizzaId,choice.item.sizeCm);
                    unit+=quote(choice.item.pizzaId,CheckoutCatalogValidator.pizzaQuote(detail,choice.item)).total-detail.basePriceForSelectedSize;
                }
                Models.SpecialQuote q=new Models.SpecialQuote();q.lineTotal=unit*bundle.quantity;return q;
            case "me":return serverProfile != null ? serverProfile : DemoData.profile();
            case "myOrders":return List.of();
            case "placeOrder": submitted++;submittedKey=(String)args[0];Models.OrderResponse order=new Models.OrderResponse();order.id=777;order.status="Pending";return order;
            case "tracking":Models.Tracking t=new Models.Tracking();t.orderId=777;t.status="Preparing";t.orderType="pickup";t.etaMinutes=10;t.branch=new Models.MapPoint();t.branch.name="Kenridge";t.branch.formattedAddress="Kenridge restaurant";return t;
            default:throw new AssertionError("Unexpected API call: "+method);
        }
    }
    private final class FixtureCall implements Call<Object> {
        private final Object value;private final boolean deferred;private boolean executed,cancelled;
        FixtureCall(Object value,boolean deferred){this.value=value;this.deferred=deferred;}
        public Response<Object> execute(){executed=true;return Response.success(value);}
        public void enqueue(Callback<Object> cb){executed=true;Runnable reply=() -> {if(!cancelled){if(value instanceof IOException)cb.onFailure(this,(IOException)value);else cb.onResponse(this,Response.success(value));}};if(deferred)pendingProfileReply=reply;else new Handler(Looper.getMainLooper()).post(reply);}
        public boolean isExecuted(){return executed;}public void cancel(){cancelled=true;}public boolean isCanceled(){return cancelled;}
        public Call<Object> clone(){return new FixtureCall(value,deferred);}public Request request(){return new Request.Builder().url("https://test.invalid/").build();}
        public Timeout timeout(){return new Timeout();}
    }
}
