package com.example.deliveryapp.data;

import com.example.deliveryapp.util.Money;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Built-in demo account with sample data, so the whole app can be tried without the backend running.
 * Log in with DEMO_EMAIL / DEMO_PASSWORD. While signed in as the demo user, Repo answers every call from here
 * instead of the network, and orders are fake.
 *
 * !! Before releasing the app to real customers set ENABLED = false (it is a login that skips the server). !!
 */
public final class DemoData {

    private DemoData() {}

    public static final boolean ENABLED = false;
    public static final String EMAIL = "demo@delivery.app";
    public static final String PASSWORD = "Demo@1234";
    static final String TOKEN = "demo-token";

    public static boolean isDemoLogin(String email, String password) {
        return ENABLED && email != null && password != null
                && EMAIL.equalsIgnoreCase(email.trim()) && PASSWORD.equals(password);
    }

    /** True while the signed-in user is the demo account. */
    public static boolean isActive() {
        return ENABLED && Prefs.get() != null && TOKEN.equals(Prefs.get().getToken());
    }

    public static Models.AuthResponse auth() {
        Models.AuthResponse a = new Models.AuthResponse();
        a.accessToken = TOKEN;
        a.tokenType = "Bearer";
        a.expiresIn = 0;          // never expires
        a.username = EMAIL;
        a.roles = Arrays.asList("ROLE_USER");
        return a;
    }

    // ------------------------------------------------------------------ profile
    public static Models.Profile profile() {
        Models.Profile p = new Models.Profile();
        p.firstName = "Demo";
        p.lastName = "Customer";
        p.email = EMAIL;
        p.phone = "0821234567";
        p.preferredStore = "Kenridge";
        Models.AddressSelection a = new Models.AddressSelection();
        a.googlePlaceId = "demo-place-id";
        a.formattedAddress = "12 Main Road, Kenridge, Durbanville, 7550";
        a.latitude = -33.8337;
        a.longitude = 18.6456;
        a.houseNumber = "12";
        a.street = "Main Road";
        a.area = "Kenridge";
        a.city = "Durbanville";
        a.postalCode = "7550";
        a.province = "Western Cape";
        a.country = "South Africa";
        p.address = a;
        return p;
    }

    // ------------------------------------------------------------------ pizzas
    private static final double[] SIZE_PRICE = {79, 109, 149};
    private static final int[] SIZES = {20, 30, 40};
    private static final double TOPPING_PRICE = 12.0;

    private static final String[] TOPPING_NAMES = {
            "Mozzarella", "Pepperoni", "Mushrooms", "Green peppers", "Red onion",
            "Bacon", "Olives", "Grilled chicken", "Pineapple", "Feta"};

    private static final class Pizza {
        final int id; final String name; final double premium; final int[] defaults;
        Pizza(int id, String name, double premium, int... defaults) {
            this.id = id; this.name = name; this.premium = premium; this.defaults = defaults;
        }
    }

    private static final Pizza[] PIZZAS = {
            new Pizza(1, "Margherita", 0, 1),
            new Pizza(2, "Pepperoni", 0, 1, 2),
            new Pizza(3, "BBQ Chicken", 0, 1, 8, 5),
            new Pizza(4, "Meat Lovers", 20, 1, 2, 6, 8),
            new Pizza(5, "Veg Supreme", 20, 1, 3, 4, 5, 7),
    };

    private static String toppingName(int id) { return TOPPING_NAMES[id - 1]; }

    private static Pizza pizza(int id) {
        for (Pizza p : PIZZAS) if (p.id == id) return p;
        return PIZZAS[0];
    }

    private static double sizePrice(Pizza p, int sizeCm) {
        for (int i = 0; i < SIZES.length; i++) if (SIZES[i] == sizeCm) return SIZE_PRICE[i] + p.premium;
        return SIZE_PRICE[1] + p.premium;
    }

    public static Models.PizzaCategorySplit pizzas() {
        Models.PizzaCategorySplit split = new Models.PizzaCategorySplit();
        split.favouritePizzas = new ArrayList<>();
        split.supremePizzas = new ArrayList<>();
        for (Pizza p : PIZZAS) {
            Models.PizzaCard c = new Models.PizzaCard();
            c.pizzaId = p.id;
            c.name = p.name;
            c.defaultSizeCm = 30;
            c.basePrice = sizePrice(p, 20);   // "from" price
            c.defaultToppings = new ArrayList<>();
            for (int t : p.defaults) c.defaultToppings.add(toppingName(t));
            (p.premium > 0 ? split.supremePizzas : split.favouritePizzas).add(c);
        }
        return split;
    }

    public static Models.PizzaDetail pizzaDetail(int pizzaId, Integer sizeCm) {
        Pizza p = pizza(pizzaId);
        int size = sizeCm == null ? 30 : sizeCm;
        Models.PizzaDetail d = new Models.PizzaDetail();
        d.pizzaId = p.id;
        d.name = p.name;
        d.basePriceForSelectedSize = sizePrice(p, size);
        d.availableSizes = new ArrayList<>();
        for (int s : SIZES) {
            Models.SizePrice sp = new Models.SizePrice();
            sp.sizeCm = s; sp.basePrice = sizePrice(p, s);
            d.availableSizes.add(sp);
        }
        d.defaultToppings = new ArrayList<>();
        for (int t : p.defaults) d.defaultToppings.add(topping(t));
        d.allToppings = new ArrayList<>();
        d.extraPrices = new ArrayList<>();
        for (int t = 1; t <= TOPPING_NAMES.length; t++) {
            d.allToppings.add(topping(t));
            Models.ToppingPrice tp = new Models.ToppingPrice();
            tp.toppingId = t; tp.extraPrice = TOPPING_PRICE;
            d.extraPrices.add(tp);
        }
        d.baseOptions = new ArrayList<>();
        d.baseOptions.add(base(1, "Classic", 0));
        d.baseOptions.add(base(2, "Thin crust", 0));
        d.baseOptions.add(base(3, "Stuffed crust", 25));
        return d;
    }

    private static Models.ToppingItem topping(int id) {
        Models.ToppingItem t = new Models.ToppingItem();
        t.toppingId = id; t.name = toppingName(id);
        return t;
    }

    private static Models.PizzaBaseOption base(int id, String name, double extra) {
        Models.PizzaBaseOption b = new Models.PizzaBaseOption();
        b.pizzaBaseOptionId = id; b.name = name; b.extraPrice = extra;
        return b;
    }

    public static Models.QuoteResponse quote(int pizzaId, Models.QuoteRequest req) {
        double total = sizePrice(pizza(pizzaId), req.sizeCm);
        if (req.pizzaBaseOptionId != null && req.pizzaBaseOptionId == 3) total += 25;
        if (req.selectedToppingIds != null) total += TOPPING_PRICE * req.selectedToppingIds.size();
        Models.QuoteResponse q = new Models.QuoteResponse();
        q.total = total;
        return q;
    }

    // ------------------------------------------------------------------ menu items
    public static List<Models.MenuItem> menuItems() {
        List<Models.MenuItem> list = new ArrayList<>();

        Models.MenuItem beef = item(101, "Classic Beef Burger", "Burgers", 69,
                "Flame-grilled beef patty, lettuce, tomato and cheese on a toasted bun.");
        beef.burgerToppings = Arrays.asList(
                component(1001, "Beef patty", true, 0, "protein", false),
                component(1002, "Chicken fillet", false, 5, "protein", true),
                component(1003, "Lettuce", true, 0, "topping", true),
                component(1004, "Tomato", true, 0, "topping", true),
                component(1005, "Cheese slice", true, 0, "topping", true),
                component(1006, "Bacon", false, 12, "topping", true),
                component(1007, "Fried egg", false, 8, "topping", true));
        beef.modifierGroups = Arrays.asList(group(1, "Choose a side", true, 1, 1,
                option(2001, "Chips", 0), option(2002, "Onion rings", 15), option(2003, "Side salad", 10)));
        list.add(beef);

        Models.MenuItem spicy = item(102, "Spicy Chicken Burger", "Burgers", 75,
                "Crispy chicken fillet with jalapeños and peri-peri mayo.");
        spicy.burgerToppings = Arrays.asList(
                component(1101, "Chicken fillet", true, 0, "protein", false),
                component(1102, "Lettuce", true, 0, "topping", true),
                component(1103, "Jalapeños", true, 0, "topping", true),
                component(1104, "Cheese slice", false, 8, "topping", true));
        spicy.modifierGroups = beef.modifierGroups;
        list.add(spicy);

        Models.MenuItem pasta = item(201, "Creamy Chicken Pasta", "Pasta", 85,
                "Penne in a creamy mushroom sauce with grilled chicken.");
        pasta.modifierGroups = Arrays.asList(group(2, "Extras", false, 0, 2,
                option(3001, "Extra cheese", 10), option(3002, "Garlic bread", 18)));
        list.add(pasta);

        list.add(item(202, "Bolognese", "Pasta", 79, "Slow-cooked beef ragù over spaghetti."));

        Models.MenuItem cola = item(301, "Coca-Cola", "Drinks", 18, null);
        cola.is300ml = true;
        list.add(cola);
        Models.MenuItem cola2 = item(302, "Coca-Cola", "Drinks", 35, null);
        cola2.is2l = true;
        list.add(cola2);
        Models.MenuItem water = item(303, "Sparkling water", "Drinks", 16, null);
        water.is300ml = true;
        list.add(water);

        list.add(item(401, "Chocolate brownie", "Desserts", 38, "Warm brownie with a scoop of vanilla ice cream."));
        return list;
    }

    private static Models.MenuItem item(int id, String name, String category, double price, String description) {
        Models.MenuItem m = new Models.MenuItem();
        m.menuItemId = id; m.name = name; m.categoryName = category; m.price = price; m.description = description;
        m.modifierGroups = new ArrayList<>();
        m.burgerToppings = new ArrayList<>();
        return m;
    }

    private static Models.BurgerTopping component(int id, String name, boolean def, double price, String type, boolean removable) {
        Models.BurgerTopping t = new Models.BurgerTopping();
        t.toppingId = id; t.name = name; t.defaultSelected = def; t.price = price;
        t.componentType = type; t.removable = removable;
        return t;
    }

    private static Models.ModifierGroup group(int id, String name, boolean required, int min, int max, Models.ModifierOption... options) {
        Models.ModifierGroup g = new Models.ModifierGroup();
        g.groupId = id; g.name = name; g.required = required;
        g.minSelect = min; g.maxSelect = max;
        g.options = Arrays.asList(options);
        return g;
    }

    private static Models.ModifierOption option(int id, String name, double price) {
        Models.ModifierOption o = new Models.ModifierOption();
        o.optionId = id; o.name = name; o.price = price;
        return o;
    }

    // ------------------------------------------------------------------ orders
    public static Models.OrderResponse placeOrder(Models.OrderRequest req) {
        Models.OrderResponse r = new Models.OrderResponse();
        r.id = 1000 + (System.currentTimeMillis() / 1000) % 90000;
        r.status = "Pending";
        r.branchName = req.branchName;
        r.orderType = req.orderType;
        r.createdAt = Money.dateTime(System.currentTimeMillis());
        return r;
    }

    /** No PayFast page in demo mode (signing needs the server); the app goes straight to the "paid" check. */
    public static Models.PayFastCheckoutResponse payFastCheckout(Models.OrderRequest req) {
        Models.PayFastCheckoutResponse r = new Models.PayFastCheckoutResponse();
        r.order = placeOrder(req);
        return r;
    }

    public static Models.PaymentStatus paymentStatus() {
        Models.PaymentStatus s = new Models.PaymentStatus();
        s.status = "PAID";
        return s;
    }

    /**
     * Fake progress for a demo order, from that order's own age only (so placing a new order never changes an older one):
     * Pending → Preparing → Out for delivery / Ready for collection → Delivered / Collected.
     */
    static String demoStatus(Models.PastOrder p) {
        long ms = System.currentTimeMillis() - p.placedAtMillis;
        boolean pickup = "pickup".equals(p.orderType);
        if (ms < DEMO_PREPARING_AT) return "Pending";
        if (ms < DEMO_OUT_AT) return "Preparing";
        if (ms < DEMO_DONE_AT) return pickup ? "Ready for collection" : "Out for delivery";
        return pickup ? "Collected" : "Delivered";
    }

    // Demo timeline is short on purpose, so the tracking screen can be watched from start to finish.
    private static final long DEMO_PREPARING_AT = 60_000;      // 1 min
    private static final long DEMO_OUT_AT = 3 * 60_000;        // 3 min
    private static final long DEMO_DONE_AT = 8 * 60_000;       // 8 min

    /** Rough pins for the demo only – the real branch location comes from the server. */
    private static Models.MapPoint demoBranch(String name) {
        Models.MapPoint b = new Models.MapPoint();
        boolean uitzicht = Branch.UITZICHT.displayName.equalsIgnoreCase(name);
        b.name = uitzicht ? Branch.UITZICHT.displayName : Branch.KENRIDGE.displayName;
        b.latitude = uitzicht ? -33.8250 : -33.8380;
        b.longitude = uitzicht ? 18.6650 : 18.6380;
        return b;
    }

    /** Fake live tracking: a driver is assigned while the order is prepared, then drives straight to the address. */
    public static Models.Tracking tracking(long orderId) {
        Models.PastOrder p = null;
        for (Models.PastOrder o : Prefs.get().getHistory()) if (o.id == orderId) { p = o; break; }

        Models.Tracking t = new Models.Tracking();
        t.orderId = orderId;
        if (p == null) {   // the built-in sample order
            t.status = "Delivered";
            t.orderType = "delivery";
            t.branch = demoBranch("Kenridge");
            return t;
        }
        t.status = demoStatus(p);
        t.orderType = p.orderType;
        t.branch = demoBranch(p.branchName);
        if ("pickup".equals(p.orderType)) {
            long left = DEMO_OUT_AT - (System.currentTimeMillis() - p.placedAtMillis);
            t.etaMinutes = left > 0 ? (int) Math.ceil(left / 60_000.0) : null;
            return t;
        }

        t.destination = new Models.MapPoint();
        t.destination.name = "Your address";
        Models.AddressSelection a = p.deliveryAddress;
        if (a != null && a.latitude != null && a.longitude != null) {
            t.destination.formattedAddress = a.formattedAddress;
            t.destination.latitude = a.latitude;
            t.destination.longitude = a.longitude;
        } else {
            t.destination.latitude = t.branch.latitude + 0.012;
            t.destination.longitude = t.branch.longitude + 0.010;
        }

        long ms = System.currentTimeMillis() - p.placedAtMillis;
        if (ms >= DEMO_PREPARING_AT && ms < DEMO_DONE_AT) {
            t.driver = new Models.Driver();
            t.driver.name = "Sipho (demo driver)";
            t.driver.phone = "0820000000";
            t.driver.vehicle = "Red scooter · CA 123-456";
            double f = ms < DEMO_OUT_AT ? 0 : (ms - DEMO_OUT_AT) / (double) (DEMO_DONE_AT - DEMO_OUT_AT);
            t.driver.latitude = t.branch.latitude + (t.destination.latitude - t.branch.latitude) * f;
            t.driver.longitude = t.branch.longitude + (t.destination.longitude - t.branch.longitude) * f;
            t.driver.locationUpdatedAt = Money.dateTime(System.currentTimeMillis());
            t.etaMinutes = (int) Math.ceil((DEMO_DONE_AT - ms) / 60_000.0);
        } else if (ms < DEMO_PREPARING_AT) {
            t.etaMinutes = (int) Math.ceil((DEMO_DONE_AT - ms) / 60_000.0);
        }
        return t;
    }

    /** Orders placed in demo mode (status follows each order's age), plus one sample past order. */
    public static List<Models.OrderHistoryEntry> myOrders() {
        List<Models.OrderHistoryEntry> list = new ArrayList<>();
        for (Models.PastOrder p : Prefs.get().getHistory()) {
            Models.OrderHistoryEntry o = new Models.OrderHistoryEntry();
            o.id = p.id;
            o.status = demoStatus(p);
            o.branchName = p.branchName;
            o.orderType = p.orderType;
            o.createdAt = Money.dateTime(p.placedAtMillis);
            o.total = p.estimatedTotal;
            o.itemLines = new ArrayList<>(Models.nz(p.lines));
            list.add(o);
        }
        Models.OrderHistoryEntry sample = new Models.OrderHistoryEntry();
        sample.id = 1001;
        sample.status = "Delivered";
        sample.branchName = "Kenridge";
        sample.orderType = "delivery";
        sample.createdAt = "Sample order";
        sample.total = 218.0;
        sample.itemLines = Arrays.asList("1× Pepperoni (30cm · Classic)", "1× Coca-Cola");
        list.add(sample);
        return list;
    }
}
