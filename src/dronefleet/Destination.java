package dronefleet;

public class Destination {
    private String name;
    private String address;
    // Putem stoca si coordonatele daca le stim, dar le vom afla prin API
    
    public Destination(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
}