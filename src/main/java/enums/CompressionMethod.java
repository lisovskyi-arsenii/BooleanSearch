package enums;

import lombok.Getter;

@Getter
public enum CompressionMethod {
    VBC("vbc"),
    G("g");

    private final String method;

    CompressionMethod(String method) {
        this.method = method;
    }
}
