package com.example.deliveryapp.data;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DriverModelsTest {
    @Test public void rolesAreExactAndStaffDoesNotBecomeCustomer() {
        assertEquals(AccountRole.DRIVER,AccountRole.from(List.of("ROLE_DRIVER")));
        assertEquals(AccountRole.STAFF,AccountRole.from(List.of("ROLE_USER","ROLE_ADMIN","ROLE_SUPER_ADMIN")));
        assertEquals(AccountRole.CUSTOMER,AccountRole.from(List.of("role_user")));
        assertEquals(AccountRole.UNKNOWN,AccountRole.from(List.of("NOT_A_USER")));
        assertEquals(AccountRole.UNKNOWN,AccountRole.from(null));
    }
    @Test public void pickupAllowsDriverCollectionWithoutKitchenCompletion() {
        DriverModels.Delivery d=new DriverModels.Delivery(); d.state="ASSIGNED"; d.order=new DriverModels.Order();
        d.order.status="Pending"; assertTrue(d.unfinished()); assertTrue(d.pickupAllowed());
        d.order.status="Preparing"; assertTrue(d.pickupAllowed());
        d.order.status="Rejected"; assertFalse(d.pickupAllowed());
        d.order.status="Cancelled"; assertFalse(d.pickupAllowed());
        d.order.status="Completed"; assertTrue(d.pickupAllowed());
        d.state="PICKED_UP"; assertTrue(d.unfinished()); assertFalse(d.pickupAllowed());
        d.state="DELIVERED"; assertFalse(d.unfinished());
    }
    @Test public void payAndSalesOnlyCountDeliveredAndStopAtClockOut() {
        DriverModels.Dashboard d=new DriverModels.Dashboard(); d.shift=new DriverModels.Shift();
        d.shift.clockedInAt="2026-10-21T17:00:00+02:00"; d.shift.clockedOutAt="2026-10-21T18:30:00+02:00";
        d.shift.hourlyRate=60; d.shift.perDeliveryRate=10;
        DriverModels.Delivery delivered=new DriverModels.Delivery();delivered.state="DELIVERED";delivered.total=150;delivered.order=new DriverModels.Order();
        DriverModels.Delivery active=new DriverModels.Delivery();active.state="ASSIGNED";active.total=200;active.order=new DriverModels.Order();
        d.deliveries=List.of(delivered,active);
        assertEquals(150,d.sales(),.001);assertEquals(1,d.deliveredCount());assertTrue(d.hasUnfinished());
        assertEquals(100,d.estimatedPay(DriverModels.timestamp("2026-10-22T08:00:00+02:00")),.001);
        active.state="DELIVERED";assertFalse(d.hasUnfinished());
    }
    @Test public void currentAndHistoryAreSeparateAndScopedToShiftWithNewestCompletionsFirst() {
        DriverModels.Dashboard dashboard=new DriverModels.Dashboard(); dashboard.shift=new DriverModels.Shift(); dashboard.shift.id=99;
        DriverModels.Delivery assigned=delivery(1,99,"ASSIGNED",null);
        DriverModels.Delivery picked=delivery(2,99,"PICKED_UP",null);
        DriverModels.Delivery older=delivery(3,99,"DELIVERED","2026-10-21T18:00:00+02:00");
        DriverModels.Delivery newer=delivery(4,99,"DELIVERED","2026-10-21T19:00:00+02:00");
        DriverModels.Delivery previous=delivery(5,98,"DELIVERED","2026-10-20T19:00:00+02:00");
        DriverModels.Delivery cancelled=delivery(6,99,"CANCELLED",null);
        dashboard.deliveries=List.of(older,assigned,newer,previous,cancelled,picked);
        assertEquals(List.of(assigned,picked),dashboard.currentDeliveries());
        assertEquals(List.of(newer,older),dashboard.completedDeliveries());
        assertEquals(2,dashboard.deliveredCount()); assertEquals(20,dashboard.sales(),.001);
        assertEquals(older,dashboard.deliveries.get(0));
        dashboard.shift=null;
        assertTrue(dashboard.currentDeliveries().isEmpty()); assertTrue(dashboard.completedDeliveries().isEmpty());
    }
    private static DriverModels.Delivery delivery(long id,long shift,String state,String completed) {
        DriverModels.Delivery d=new DriverModels.Delivery();d.assignmentId=id;d.shiftId=shift;d.state=state;
        d.deliveredAt=completed;d.order=new DriverModels.Order();d.total=10;return d;
    }
    @Test public void orderSnapshotRendersMenuPizzaAndNestedSpecialContents() {
        String json="{\"menuItems\":[{\"menuItemName\":\"Burger\",\"qty\":2,\"notes\":\"No salt\",\"extras\":[{\"name\":\"Bacon\",\"qty\":1}]}],"
            +"\"pizzaItems\":[{\"pizzaName\":\"Margherita\",\"qty\":1,\"pizzaSizeCm\":30,\"pizzaBaseOptionName\":\"Thin\",\"removedIngredients\":[\"Onion\"]}],"
            +"\"specialItems\":[{\"name\":\"Combo\",\"quantity\":1,\"selections\":[{\"productName\":\"Chips\",\"menuItem\":{\"menuItemName\":\"Chips\",\"qty\":1}}]}]}";
        String contents=new Gson().fromJson(json,DriverModels.Order.class).contents();
        for(String value:List.of("2 × Burger","No salt","Bacon","30cm","Thin","No Onion","Combo","Chips"))
            assertTrue(value,contents.contains(value));
    }
    @Test public void addressPrefersConfirmedAddressAndHandlesMissingFields() {
        DriverModels.Order o=new DriverModels.Order();assertEquals("",o.address());
        o.houseNumber="12";o.street="Main Road";o.city="Cape Town";assertEquals("12, Main Road, Cape Town",o.address());
        o.formattedAddress="12 Main Road, Cape Town";assertEquals(o.formattedAddress,o.address());
    }
}
