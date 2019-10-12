package pcbe.stock.model;

import static java.util.Objects.isNull;

/**
 * Response returned by the server to client requests.
 */
public class Response {
    private String errorMessage;

    private Response(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return true if the request finished successfuly
     */
    public boolean isSuccessful() {
        return isNull(errorMessage);
    }

    /**
     * @param errorMessage 
     * @return a failing response with the given error message
     */
    public static Response failed(String errorMessage) {
        return new Response(errorMessage);
    }

    /**
     * @return a successful response
     */
	public static Response successful() {
        return new Response(null);
	}
}
