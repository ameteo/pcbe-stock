package pcbe.stock.model.request;

import pcbe.stock.model.response.RegisterResponse;
import pcbe.stock.server.api.API;

public class RegisterRequest extends AbstractRequest<RegisterResponse> {
    private RegisterRequest(String id) {
        super(id);
    }

    @Override
    public String getPath() {
        return API.REGISTER;
    }

    @Override
    public Class<RegisterResponse> getResultClass() {
        return RegisterResponse.class;
    }

    public static RegisterRequest withId(String id) {
        return new RegisterRequest(id);
    }
}
