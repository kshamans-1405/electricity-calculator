
package electricity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SeasonalConsumptionEstimator {

    public enum Season {
        SUMMER, WINTER, RAINY
    }

    private final BillCalculator calculator;

    public SeasonalConsumptionEstimator(BillCalculator calculator) {
        if (calculator == null || calculator.isEmpty()) {
            throw new IllegalArgumentException(
                "Bill calculator must contain appliances."
            );
        }

        this.calculator = calculator;
    }

    // Select a season
    public Season selectSeason(String seasonName) {
        if (seasonName == null || seasonName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Please select a season."
            );
        }

        try {
            return Season.valueOf(
                seasonName.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid season. Choose Summer, Winter or Rainy."
            );
        }
    }

    // Check whether the appliance is an air conditioner
    private boolean isAirConditioner(String name) {
        String applianceName = name.toLowerCase(Locale.ROOT).trim();

        return applianceName.equals("ac")
            || applianceName.startsWith("ac ")
            || applianceName.startsWith("ac-")
            || applianceName.contains("air conditioner");
    }

    // Adjust appliance hours according to the selected season
    public double getSeasonalHours(
            Appliance appliance, Season season) {

        if (appliance == null || season == null) {
            throw new IllegalArgumentException(
                "Appliance and season cannot be null."
            );
        }

        String name = appliance.getName().toLowerCase(Locale.ROOT);
        double originalHours = appliance.getHoursPerDay();
        double factor = 1.0;

        switch (season) {

            case SUMMER:
                if (isAirConditioner(name)) {
                    factor = 1.5;
                } else if (name.contains("fan")) {
                    factor = 1.3;
                } else if (name.contains("geyser")
                        || name.contains("water heater")) {
                    factor = 0.7;
                } else if (name.contains("light")
                        || name.contains("bulb")) {
                    factor = 0.95;
                }
                break;

            case WINTER:
                if (isAirConditioner(name)) {
                    factor = 0.4;
                } else if (name.contains("fan")) {
                    factor = 0.7;
                } else if (name.contains("geyser")
                        || name.contains("water heater")) {
                    factor = 1.5;
                }
                break;

            case RAINY:
                if (name.contains("fan")) {
                    factor = 0.85;
                } else if (name.contains("light")
                        || name.contains("bulb")) {
                    factor = 1.15;
                }
                break;

            default:
                break;
        }

        return Math.min(24.0, originalHours * factor);
    }

    // Calculate daily consumption of one appliance
    public double calculateApplianceDailyUnits(
            Appliance appliance, Season season) {

        double hours = getSeasonalHours(appliance, season);

        return (appliance.getWatts()
                * hours
                * appliance.getQuantity()) / 1000.0;
    }

    // Calculate monthly consumption of one appliance
    public double calculateApplianceSeasonalUnits(
            Appliance appliance, Season season) {

        return calculateApplianceDailyUnits(appliance, season)
                * Constants.DAYS_PER_MONTH;
    }

    // Calculate total monthly units for a season
    public double calculateSeasonalUnits(Season season) {

        if (season == null) {
            throw new IllegalArgumentException(
                "Season cannot be null."
            );
        }

        double total = 0;

        for (Appliance appliance : calculator.getAppliances()) {
            total += calculateApplianceSeasonalUnits(
                appliance, season
            );
        }

        return total;
    }

    // Calculate the monthly electricity bill
    public double calculateSeasonalBill(Season season) {

        double units = calculateSeasonalUnits(season);

        return calculator.getTariff().calculateBill(units);
    }

    // Store seasonal comparison results
    public static class SeasonalResult {

        private final Season season;
        private final double dailyUnits;
        private final double monthlyUnits;
        private final double monthlyBill;

        public SeasonalResult(
                Season season,
                double dailyUnits,
                double monthlyUnits,
                double monthlyBill) {

            this.season = season;
            this.dailyUnits = dailyUnits;
            this.monthlyUnits = monthlyUnits;
            this.monthlyBill = monthlyBill;
        }

        public Season getSeason() {
            return season;
        }

        public double getDailyUnits() {
            return dailyUnits;
        }

        public double getMonthlyUnits() {
            return monthlyUnits;
        }

        public double getMonthlyBill() {
            return monthlyBill;
        }
    }

    // Compare all three seasons
    public List<SeasonalResult> compareSeasonalBills() {

        List<SeasonalResult> results = new ArrayList<>();

        for (Season season : Season.values()) {

            double monthlyUnits = calculateSeasonalUnits(season);

            double dailyUnits =
                monthlyUnits / Constants.DAYS_PER_MONTH;

            double monthlyBill =
                calculator.getTariff().calculateBill(monthlyUnits);

            results.add(new SeasonalResult(
                season,
                dailyUnits,
                monthlyUnits,
                monthlyBill
            ));
        }

        return results;
    }

    // Display appliance-wise seasonal consumption and bill
    public String displaySeasonalReport(Season season) {

        if (season == null) {
            throw new IllegalArgumentException(
                "Season cannot be null."
            );
        }

        StringBuilder report = new StringBuilder();

        report.append("\n========== SEASONAL CONSUMPTION REPORT ==========\n");
        report.append("Season: ").append(season).append("\n\n");

        report.append(String.format(
            "%-18s %-12s %-12s %-12s%n",
            "Appliance",
            "Hours/Day",
            "Daily Units",
            "Monthly Units"
        ));

        report.append(
            "---------------------------------------------------------------\n"
        );

        double totalDailyUnits = 0;
        double totalMonthlyUnits = 0;

        for (Appliance appliance : calculator.getAppliances()) {

            double hours = getSeasonalHours(appliance, season);

            double dailyUnits =
                calculateApplianceDailyUnits(appliance, season);

            double monthlyUnits =
                calculateApplianceSeasonalUnits(appliance, season);

            totalDailyUnits += dailyUnits;
            totalMonthlyUnits += monthlyUnits;

            report.append(String.format(
                "%-18s %-12.2f %-12.2f %-12.2f%n",
                appliance.getName(),
                hours,
                dailyUnits,
                monthlyUnits
            ));
        }

        double bill =
            calculator.getTariff().calculateBill(totalMonthlyUnits);

        report.append(
            "---------------------------------------------------------------\n"
        );

        report.append(String.format(
            "Total daily consumption : %.2f kWh%n",
            totalDailyUnits
        ));

        report.append(String.format(
            "Total monthly units     : %.2f kWh%n",
            totalMonthlyUnits
        ));

        report.append(String.format(
            "Estimated monthly bill  : Rs. %.2f%n",
            bill
        ));

        return report.toString();
    }

    // Display comparison of all seasonal bills
    public String displaySeasonalComparison() {

        StringBuilder report = new StringBuilder();

        report.append("\n========== SEASONAL BILL COMPARISON ==========\n");

        report.append(String.format(
            "%-12s %-16s %-18s%n",
            "Season",
            "Monthly Units",
            "Monthly Bill (Rs.)"
        ));

        report.append(
            "------------------------------------------------\n"
        );

        for (SeasonalResult result : compareSeasonalBills()) {

            report.append(String.format(
                "%-12s %-16.2f %-18.2f%n",
                result.getSeason(),
                result.getMonthlyUnits(),
                result.getMonthlyBill()
            ));
        }

        return report.toString();
    }
}
