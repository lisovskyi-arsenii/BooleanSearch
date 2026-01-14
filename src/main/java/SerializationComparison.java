public class SerializationComparison {
    public final FormatMetrics binary;
    public final FormatMetrics text;
    public final FormatMetrics json;

    public SerializationComparison(FormatMetrics binary, FormatMetrics text, FormatMetrics json) {
        this.binary = binary;
        this.text = text;
        this.json = json;
    }

    // TODO
    public void printData() {

    }
}
