package pcbe.stock.model.request;

import pcbe.stock.model.response.Response;

public interface Request<T extends Response> {
    String getClientId();
    String getPath();
	Class<T> getResultClass();
}