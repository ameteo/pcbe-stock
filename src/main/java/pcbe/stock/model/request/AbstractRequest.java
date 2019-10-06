package pcbe.stock.model.request;

import pcbe.stock.model.response.Response;

public abstract class AbstractRequest<T extends Response> implements Request<T> {
    private String id;

    public AbstractRequest(String id) {
        this.id = id;
    }
    public String getClientId() {
        return id;
    }
}