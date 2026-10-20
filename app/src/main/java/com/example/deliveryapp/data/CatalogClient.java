package com.example.deliveryapp.data;

import java.util.List;

/** Online-only catalog operations required before checkout. */
public interface CatalogClient {
    void freshMenu(int branchId, Repo.Result<List<Models.MenuItem>> cb);
    void freshSpecials(int branchId, Repo.Result<List<Models.Special>> cb);
    void freshPizzaDetail(int branchId, int pizzaId, Integer sizeCm, Repo.Result<Models.PizzaDetail> cb);
    void quote(int branchId, int pizzaId, Models.QuoteRequest request, Repo.Result<Models.QuoteResponse> cb);
    void specialQuote(int branchId, Models.SpecialItemRequest request, Repo.Result<Models.SpecialQuote> cb);
}
