package electricity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BillCalculator {
    private ArrayList<Appliance> appliances;
    private Tariff tariff;

    public BillCalculator(Tariff tariff) {
        this.appliances = new ArrayList<>();
        this.tariff = tariff;
    }

    public void addAppliance(Appliance appliance) {
        if (appliance == null)
            throw new IllegalArgumentException("Appliance cannot be null");
        appliances.add(appliance);
    }

    // index is 0-based
    public Appliance removeAppliance(int index) {
        if (index < 0 || index >= appliances.size())
            throw new IllegalArgumentException("No appliance at position " + (index + 1));
        return appliances.remove(index);
    }

    public double totalUnits() {
        double total = 0;
        for (Appliance a : appliances) {
            total += a.monthlyKwh();
        }
        return total;
    }

    public double totalBill() {
        return tariff.calculateBill(totalUnits());
    }

    public boolean isEmpty() {
        return appliances.isEmpty();
    }

    public int size() {
        return appliances.size();
    }

    // Read-only view, so other classes cannot change the list by accident
    public List<Appliance> getAppliances() {
        return Collections.unmodifiableList(appliances);
    }

    public Tariff getTariff() {
        return tariff;
    }

    public void setTariff(Tariff tariff) {
        if (tariff == null)
            throw new IllegalArgumentException("Tariff cannot be null");
        this.tariff = tariff;
    }
}
