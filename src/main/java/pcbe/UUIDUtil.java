package pcbe;

import java.util.UUID;

public class UUIDUtil {
	public static String prefixOf(UUID id) {
		return id.toString().substring(0, 8);
	}
    
}