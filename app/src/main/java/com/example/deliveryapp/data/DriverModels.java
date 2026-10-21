package com.example.deliveryapp.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Read models for the existing driver API. Delivery data is never cached on disk. */
public final class DriverModels {
    private DriverModels() {}
    public static class Dashboard {
        public Profile profile;
        public Shift shift;
        public List<Delivery> deliveries = new ArrayList<>();
        public boolean hasUnfinished() {
            for (Delivery d : Models.nz(deliveries)) if (d.unfinished()) return true;
            return false;
        }
        public List<Delivery> currentDeliveries() {
            List<Delivery> result = new ArrayList<>();
            if (shift != null) for (Delivery d : Models.nz(deliveries))
                if (d.shiftId == shift.id && d.unfinished() && d.order != null) result.add(d);
            return result;
        }
        public List<Delivery> completedDeliveries() {
            List<Delivery> result = new ArrayList<>();
            if (shift != null) for (Delivery d : Models.nz(deliveries))
                if (d.shiftId == shift.id && "DELIVERED".equals(d.state) && d.order != null) result.add(d);
            result.sort(java.util.Comparator.comparingLong((Delivery d) -> timestamp(d.deliveredAt)).reversed()
                    .thenComparing(java.util.Comparator.comparingLong((Delivery d) -> d.assignmentId).reversed()));
            return result;
        }
        public int deliveredCount() { return completedDeliveries().size(); }
        public double sales() {
            double sum = 0; for (Delivery d : completedDeliveries()) sum += d.total; return sum;
        }
        public double estimatedPay(long now) {
            if (shift == null || timestamp(shift.clockedInAt) == 0) return 0;
            long end = shift.clockedOutAt == null ? now : timestamp(shift.clockedOutAt);
            long minutes = Math.max(0, (end - timestamp(shift.clockedInAt)) / 60000);
            return shift.hourlyRate * minutes / 60.0 + shift.perDeliveryRate * deliveredCount();
        }
    }
    public static class Profile {
        public long id;
        public int branchId;
        public String firstName, lastName, email;
        public boolean active;
    }
    public static class Shift {
        public long id, driverId;
        public int branchId;
        public String businessDate, clockedInAt, clockedOutAt, state;
        public double hourlyRate, perDeliveryRate;
        public boolean active() { return "ACTIVE".equals(state); }
    }
    public static class Delivery {
        public long assignmentId, driverId, shiftId;
        public int branchId;
        public Order order;
        public double total;
        public String state, assignedAt, pickedUpAt, deliveredAt;
        public boolean selfClaimed;
        public boolean unfinished() { return "ASSIGNED".equals(state) || "PICKED_UP".equals(state); }
        public boolean pickupAllowed() { return "ASSIGNED".equals(state) && order != null
                && ("Pending".equals(order.status) || "Preparing".equals(order.status) || "Completed".equals(order.status)); }
    }
    public static class Order {
        public long id;
        public String branchName, status, customerName, phone, formattedAddress, houseNumber, street,
                area, city, postalCode, complexName, gateAccessCode, notes;
        public Double latitude, longitude;
        public List<JsonObject> menuItems, pizzaItems, specialItems;
        public String address() {
            if (formattedAddress != null && !formattedAddress.trim().isEmpty()) return formattedAddress;
            List<String> parts = new ArrayList<>();
            for (String part : new String[]{houseNumber, street, area, city, postalCode})
                if (part != null && !part.trim().isEmpty()) parts.add(part.trim());
            return String.join(", ", parts);
        }
        public String contents() {
            List<String> lines = new ArrayList<>();
            for (JsonObject item : Models.nz(menuItems)) lines.add(line(item, "menuItemName", "qty"));
            for (JsonObject item : Models.nz(pizzaItems)) lines.add(line(item, "pizzaName", "qty"));
            for (JsonObject item : Models.nz(specialItems)) {
                lines.add(line(item, "name", "quantity"));
                if (item.has("selections") && item.get("selections").isJsonArray())
                    for (JsonElement e : item.getAsJsonArray("selections")) if (e.isJsonObject()) {
                        JsonObject s = e.getAsJsonObject();
                        lines.add("  " + text(s, "productName"));
                        for (String key : new String[]{"menuItem", "pizzaItem"})
                            if (s.has(key) && s.get(key).isJsonObject())
                                lines.add("  " + line(s.getAsJsonObject(key), key.equals("menuItem") ? "menuItemName" : "pizzaName", "qty"));
                    }
            }
            return String.join("\n", lines);
        }
    }
    private static String line(JsonObject item, String name, String qty) {
        StringBuilder b = new StringBuilder(text(item, qty)).append(" × ").append(text(item, name));
        for (String key : new String[]{"pizzaSizeCm", "pizzaBaseOptionName", "notes"}) {
            String value = text(item, key);
            if (!value.isEmpty()) b.append(" · ").append(value).append(key.equals("pizzaSizeCm") ? "cm" : "");
        }
        if (item.has("extras") && item.get("extras").isJsonArray())
            for (JsonElement e : item.getAsJsonArray("extras")) if (e.isJsonObject()) {
                JsonObject x = e.getAsJsonObject(); String label = text(x, "name");
                if (label.isEmpty()) label = text(x, "ingredientName");
                b.append("\n  + ").append(text(x, "qty")).append(" × ").append(label);
            }
        if (item.has("removedIngredients") && item.get("removedIngredients").isJsonArray())
            for (JsonElement e : item.getAsJsonArray("removedIngredients")) if (e.isJsonPrimitive())
                b.append("\n  No ").append(e.getAsString());
        return b.toString();
    }
    private static String text(JsonObject item, String key) {
        return item.has(key) && item.get(key).isJsonPrimitive() ? item.get(key).getAsString() : "";
    }
    public static long timestamp(String iso) {
        try { return OffsetDateTime.parse(iso).toInstant().toEpochMilli(); }
        catch (RuntimeException e) { return 0; }
    }
}
