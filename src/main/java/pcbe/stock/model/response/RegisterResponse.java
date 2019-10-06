package pcbe.stock.model.response;

import static java.util.Objects.isNull;

public class RegisterResponse implements Response {
    private String error;

    private RegisterResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean isSuccessful() {
        return isNull(error);
    }

    public static RegisterResponse failed(String error) {
        return new RegisterResponse(error);
    }

	public static RegisterResponse successful() {
        return new RegisterResponse(null);
	}
}
