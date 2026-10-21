package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON shapes exchanged with the Spring Boot backend (Gson maps fields by name). */
public final class Models {
    private Models() {}

    public static <T> List<T> nz(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }

    // ------------------------------------------------------------ auth
    public static class LoginRequest {
        public String username;
        public String password;
        public LoginRequest(String username, String password) { this.username = username; this.password = password; }
    }

    public static class AuthResponse {
        public String accessToken;
        public String tokenType;
        public long expiresIn;
        public String username;
        public List<String> roles;
    }

    public static class ForgotPasswordRequest {
        public String email;
        public ForgotPasswordRequest(String email) { this.email = email; }
    }

    public static class MessageResponse {
        public String message;
    }

    /** Customer signup request for POST /api/auth/register. */
    public static class RegisterRequest {
        public String firstName;
        public String lastName;
        public String email;
        public String password;
        public String phone;
        public String phone2;
        public String preferredStore;
        public AddressSelection address;
    }

    /** Customer details returned by GET /api/me and cached for checkout. */
    public static class Profile {
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String phone2;
        public String preferredStore;
        public String complexName;
        public AddressSelection address;
        /** only sent on PUT when the customer changes their password */
        public String currentPassword;
        public String newPassword;

        public String fullName() {
            String f = firstName == null ? "" : firstName.trim();
            String l = lastName == null ? "" : lastName.trim();
            return (f + " " + l).trim();
        }
    }

    // ------------------------------------------------------------ menu items
    public static class MenuItem {
        public int menuItemId;
        public String name;
        public String description;
        public Integer categoryId;
        public String categoryName;
        public Double price;
        public boolean is300ml;
        public boolean is2l;
        public List<ModifierGroup> modifierGroups;
        public List<BurgerTopping> burgerToppings;

        public double priceOrZero() { return price == null ? 0.0 : price; }
    }

    public static class ModifierGroup {
        public int groupId;
        public String name;
        public boolean required;
        public Integer minSelect;
        public Integer maxSelect;
        public List<ModifierOption> options;

        public int minRequired() {
            int m = minSelect == null ? 0 : minSelect;
            return Math.max(m, required ? 1 : 0);
        }
        /** 0 = unlimited */
        public int maxAllowed() { return maxSelect == null ? 0 : maxSelect; }
    }

    public static class ModifierOption {
        public int optionId;
        public String name;
        public Integer menuItemId;
        public Double price;
    }

    public static class BurgerTopping {
        public int toppingId;
        public String name;
        public boolean defaultSelected;
        public Double price;
        public String componentType;
        public Boolean removable;
        public Integer proteinQuantityRequired;

        public boolean isProtein() { return "protein".equalsIgnoreCase(componentType); }
        public boolean isRemovable() { return removable == null || removable; }
        public double priceOrZero() { return price == null ? 0.0 : price; }
    }

    // ------------------------------------------------------------ pizzas
    public static class PizzaCard {
        public int pizzaId;
        public String name;
        public Integer pizzaCategoryId;
        public String pizzaCategoryName;
        public Integer defaultSizeCm;
        public double basePrice;
        public List<String> defaultToppings;
    }

    public static class PizzaCategorySplit {
        public List<PizzaCard> favouritePizzas;
        public List<PizzaCard> supremePizzas;
    }

    public static class SizePrice {
        public int sizeCm;
        public double basePrice;
    }

    public static class ToppingItem {
        public int toppingId;
        public String name;
        public Boolean removable;
    }

    public static class ToppingPrice {
        public int toppingId;
        public double extraPrice;
    }

    public static class PizzaBaseOption {
        public int pizzaBaseOptionId;
        public String name;
        public double extraPrice;
    }

    public static class PizzaDetail {
        public int pizzaId;
        public String name;
        public List<SizePrice> availableSizes;
        public double basePriceForSelectedSize;
        public List<ToppingItem> defaultToppings;
        public List<ToppingItem> allToppings;
        public List<ToppingPrice> extraPrices;
        public List<PizzaBaseOption> baseOptions;
    }

    public static class QuoteRequest {
        public int sizeCm;
        public List<Integer> selectedToppingIds;
        public Integer pizzaBaseOptionId;
    }

    public static class QuoteResponse {
        public double total;
    }

    public static class Special {
        public long id;
        public String name;
        public String description;
        public double price;
        public boolean availableToday;
        public List<SpecialComponent> components;
        public List<SpecialAddon> addons;
    }

    public static class SpecialComponent {
        public long id;
        public String label;
        public String productType;
        public int quantity;
        public String selectionMode;
        public Integer sizeCm;
        public boolean allowRepeats;
        public boolean allowCustomization;
        public List<SpecialOption> options;
    }

    public static class SpecialOption {
        public int id;
        public String name;
    }

    public static class SpecialAddon {
        public long id;
        public String label;
        public String productType;
        public int productId;
        public String productName;
        public Integer sizeCm;
        public double price;
        public int maxQuantity;
        public boolean allowCustomization;
        public boolean active;
    }

    public static class SpecialSelection {
        public long componentId;
        public int selectionIndex;
        public OrderItemRequest item;
        public SpecialSelection(long componentId, int selectionIndex, OrderItemRequest item) {
            this.componentId = componentId;
            this.selectionIndex = selectionIndex;
            this.item = item;
        }
    }

    public static class SpecialAddonSelection {
        public long addonId;
        public int quantity;
        public OrderItemRequest item;
        public SpecialAddonSelection(long addonId, int quantity, OrderItemRequest item) {
            this.addonId = addonId;
            this.quantity = quantity;
            this.item = item;
        }
    }

    public static class SpecialItemRequest {
        public long specialId;
        public int quantity = 1;
        public List<SpecialSelection> selections = new ArrayList<>();
        public List<SpecialAddonSelection> addons = new ArrayList<>();

        public SpecialItemRequest copyWithQuantity(int value) {
            SpecialItemRequest copy = new SpecialItemRequest();
            copy.specialId = specialId;
            copy.quantity = value;
            for (SpecialSelection s : nz(selections)) copy.selections.add(new SpecialSelection(s.componentId, s.selectionIndex,
                    s.item == null ? null : s.item.copyWithQuantity(s.item.quantity)));
            for (SpecialAddonSelection a : nz(addons)) copy.addons.add(new SpecialAddonSelection(a.addonId, a.quantity,
                    a.item == null ? null : a.item.copyWithQuantity(a.item.quantity)));
            return copy;
        }
    }

    public static class SpecialQuote {
        public double lineTotal;
    }

    // ------------------------------------------------------------ orders
    public static class Customization {
        public int id;
        public int quantity = 1;
        public String type;
        public Customization(int id, int quantity, String type) { this.id = id; this.quantity = quantity; this.type = type; }
    }

    public static class OrderItemRequest {
        public Integer menuItemId;
        public Integer pizzaId;
        public Integer sizeCm;
        public Integer pizzaBaseOptionId;
        public int quantity = 1;
        public List<Customization> customizations = new ArrayList<>();
        public String notes;

        public OrderItemRequest copyWithQuantity(int q) {
            OrderItemRequest c = new OrderItemRequest();
            c.menuItemId = menuItemId; c.pizzaId = pizzaId; c.sizeCm = sizeCm; c.pizzaBaseOptionId = pizzaBaseOptionId;
            c.quantity = q; c.notes = notes;
            for (Customization custom : nz(customizations)) c.customizations.add(new Customization(custom.id, custom.quantity, custom.type));
            return c;
        }
    }

    public static class AddressSelection {
        public String googlePlaceId;
        public String formattedAddress;
        public Double latitude;
        public Double longitude;
        public String houseNumber;
        public String street;
        public String area;
        public String city;
        public String postalCode;
        public String complexName;
        public String province;
        public String country;

        private static boolean has(String s) { return s != null && !s.trim().isEmpty(); }

        /** Same rule as the backend's AddressSelection.isVerifiedGoogleAddress(). */
        public boolean isVerified() {
            return has(googlePlaceId) && has(formattedAddress) && latitude != null && longitude != null
                    && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                    && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180
                    && has(street) && (has(area) || has(city)) && has(country);
        }

        public AddressSelection copy() {
            AddressSelection a = new AddressSelection();
            a.googlePlaceId = googlePlaceId; a.formattedAddress = formattedAddress; a.latitude = latitude;
            a.longitude = longitude; a.houseNumber = houseNumber; a.street = street; a.area = area; a.city = city;
            a.postalCode = postalCode; a.complexName = complexName; a.province = province; a.country = country;
            return a;
        }
    }

    public static class OrderRequest {
        public String customerName;
        public String phone;
        public String branchName;
        public String orderType;
        public String complexName;
        public String gateAccessCode;
        public AddressSelection deliveryAddress;
        public List<OrderItemRequest> items;
        public List<SpecialItemRequest> specialItems;
        /** "payfast" (paid in the app before the kitchen sees it) or "cash" (paid on delivery / at the counter) */
        public String paymentMethod;
    }

    /** NEW endpoint: POST /api/orders/payfast – the order plus a PayFast payment the server has already signed. */
    public static class PayFastCheckoutResponse {
        public OrderResponse order;
        public PayFastSession payment;
    }

    public static class PayFastSession {
        /** https://www.payfast.co.za/eng/process (live) or https://sandbox.payfast.co.za/eng/process */
        public String processUrl;
        /** PayFast sends the customer here when they finish / cancel; the app watches for these URLs. */
        public String returnUrl;
        public String cancelUrl;
        /** Form fields to POST to processUrl, in the order they were signed, including "signature". */
        public Map<String, String> fields;
    }

    /** NEW endpoint: GET /api/orders/{id}/payment – set by the server once PayFast's ITN arrives. */
    public static class PaymentStatus {
        /** PENDING, PAID, FAILED or CANCELLED */
        public String status;
    }

    public static class OrderResponse {
        public long id;
        public String branchName;
        public String status;
        public String orderType;
        public String createdAt;
        /** only present once the backend adds it */
        public Double total;
        /** only present on GET /api/orders/mine once the backend adds it */
        public List<OrderLine> items;
    }

    /** Customer-safe summary returned by GET /api/orders/mine. */
    public static class OrderHistoryEntry {
        public long id;
        public String createdAt;
        public String branchName;
        public String status;
        public String orderType;
        public List<String> itemLines;
        public Double total;
    }

    public static class OrderLine {
        public String name;
        public int quantity;
        public String detail;
    }

    /** Orders remembered on this phone for offline order-history fallback. */
    public static class PastOrder {
        public long id;
        public String branchName;
        public String orderType;
        public long placedAtMillis;
        public List<String> lines;
        public double estimatedTotal;
        public String status;
        /** delivery orders only; lets the demo put the destination pin on the map */
        public AddressSelection deliveryAddress;
    }

    // ------------------------------------------------------------ tracking
    /** NEW endpoint: GET /api/orders/{id}/tracking. The driver app keeps `driver` up to date on the server. */
    public static class Tracking {
        public long orderId;
        public String status;
        public String orderType;
        public MapPoint branch;
        public MapPoint destination;
        /** null until a driver has been assigned */
        public Driver driver;
        /** minutes until the order arrives / is ready; null when unknown */
        public Integer etaMinutes;
    }

    public static class MapPoint {
        public String name;
        public String formattedAddress;
        public Double latitude;
        public Double longitude;

        public boolean hasLocation() { return latitude != null && longitude != null; }
    }

    public static class Driver {
        public String name;
        public String phone;
        /** e.g. "Red scooter · CA 123-456" */
        public String vehicle;
        /** last position reported by the driver app; null if it hasn't reported yet */
        public Double latitude;
        public Double longitude;
        /** ISO date-time of that position */
        public String locationUpdatedAt;

        public boolean hasLocation() { return latitude != null && longitude != null; }
    }
}
