package pcbe.stock.model.request;

import pcbe.stock.model.response.RegisterResponse;

public class RegisterRequest extends AbstractRequest<RegisterResponse> {
    private RegisterRequest(String id) {
        super(id);
    }

    @Override
    public Class<RegisterResponse> getResultClass() {
        return RegisterResponse.class;
    }

    public static RegisterRequest withId(String id) {
        return new RegisterRequest(id);
    }
}
