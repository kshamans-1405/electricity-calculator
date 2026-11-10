package electricity;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportWriter {
    private final BillCalculator calc;
    private final SavingsAnalyzer analyzer;
    private String whatIfText;
    private WhatIfResult whatIf;

    public ReportWriter(BillCalculator calc, SavingsAnalyzer analyzer) {
        this.calc = calc;
        this.analyzer = analyzer;
    }

    // Optional: include the result of a what-if comparison in the report
    public void setWhatIf(String description, WhatIfResult result) {
        this.whatIfText = description;
        this.whatIf = result;
    }

    public String buildReport() {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(62);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));

        sb.append(line).append(String.format("%n"));
        sb.append("ELECTRICITY CONSUMPTION AND SAVINGS REPORT").append(String.format("%n"));
        sb.append("Generated on ").append(date).append(String.format("%n"));
        sb.append(line).append(String.format("%n%n"));

        sb.append("TARIFF USED").append(String.format("%n"));
        sb.append(calc.getTariff().describe()).append(String.format("%n"));

        sb.append("APPLIANCES").append(String.format("%n"));
        sb.append(String.format("%-4s%-24s%7s%10s%5s%12s%8s%n",
                "No.", "Appliance", "Watts", "Hours/day", "Qty", "Units/month", "Share"));
        List<Appliance> list = calc.getAppliances();
        for (int i = 0; i < list.size(); i++) {
            Appliance a = list.get(i);
            sb.append(String.format("%-4d%-24s%7.0f%10.1f%5d%12.1f%7.1f%%%n",
                    i + 1, a.getName(), a.getWatts(), a.getHoursPerDay(), a.getQuantity(),
                    a.monthlyKwh(), analyzer.shareOf(a)));
        }
        sb.append(String.format("%n"));

        sb.append("BILL").append(String.format("%n"));
        sb.append(String.format("  Total units per month : %.1f%n", calc.totalUnits()));
        sb.append(String.format("  Estimated bill        : Rs %.2f%n%n", calc.totalBill()));

        sb.append("SAVINGS SUGGESTIONS").append(String.format("%n"));
        List<Suggestion> tips = analyzer.suggestions();
        if (tips.isEmpty()) {
            sb.append("  No suggestions. Usage already looks efficient.").append(String.format("%n"));
        } else {
            int number = 1;
            for (Suggestion s : tips) {
                sb.append(String.format("  %d. %s%n     Saves %.1f units = Rs %.2f/month (Rs %.2f/year)%n",
                        number++, s.getText(), s.getUnitsSaved(), s.getRupeesSaved(), s.getRupeesSavedPerYear()));
            }
            sb.append("  Each saving is measured alone, so do not add them up.").append(String.format("%n"));
        }
        sb.append(String.format("%n"));

        if (whatIf != null) {
            sb.append("WHAT-IF COMPARISON").append(String.format("%n"));
            sb.append("  Change: ").append(whatIfText).append(String.format("%n"));
            sb.append(String.format("  Units per month : %.1f -> %.1f%n", whatIf.getOldUnits(), whatIf.getNewUnits()));
            sb.append(String.format("  Bill            : Rs %.2f -> Rs %.2f%n", whatIf.getOldBill(), whatIf.getNewBill()));
            if (whatIf.savingPerMonth() >= 0) {
                sb.append(String.format("  Saving          : Rs %.2f/month (Rs %.2f/year)%n",
                        whatIf.savingPerMonth(), whatIf.savingPerYear()));
            } else {
                sb.append(String.format("  Extra cost      : Rs %.2f/month (Rs %.2f/year)%n",
                        -whatIf.savingPerMonth(), -whatIf.savingPerYear()));
            }
            sb.append(String.format("%n"));
        }

        sb.append(line).append(String.format("%n"));
        return sb.toString();
    }

    public void saveToFile(String path) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(buildReport());
        }
    }
}