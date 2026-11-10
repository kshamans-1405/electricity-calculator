package electricity;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the calculator as a small web app on your own computer.
 * The Java classes (Appliance, Tariff, BillCalculator, SavingsAnalyzer, ReportWriter)
 * do all the work; this class only connects them to the browser page in the "web" folder.
 */
public class WebApp {
    private static final int FIRST_PORT = 8080;
    private static final Path WEB_DIR = Paths.get("web").toAbsolutePath().normalize();

    // One calculator for the whole app (single user, running on your own computer)
    private static BillCalculator calc = new BillCalculator(new Tariff());
    private static WhatIfResult lastWhatIf = null;
    private static String lastWhatIfText = "";

    // One API action: takes the form values sent by the page, returns JSON text
    private interface Action {
        String run(Map<String, String> params) throws Exception;
    }

    public static void main(String[] args) {
        if (!Files.isDirectory(WEB_DIR)) {
            System.out.println("Cannot find the web folder at: " + WEB_DIR);
            System.out.println("Create a folder named 'web' in the project folder and put index.html, style.css and app.js in it.");
            return;
        }

        HttpServer server = null;
        int port = FIRST_PORT;
        while (server == null && port < FIRST_PORT + 20) {
            try {
                // only this computer can open the app (loopback address)
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            } catch (IOException e) {
                port++;   // port already in use, try the next one
            }
        }
        if (server == null) {
            System.out.println("Could not start the web server: ports " + FIRST_PORT + " to " + (FIRST_PORT + 19) + " are all in use.");
            return;
        }

        server.createContext("/", WebApp::serveFile);
        server.createContext("/api/state", ex -> api(ex, p -> stateJson()));
        server.createContext("/api/add", ex -> api(ex, WebApp::addAppliance));
        server.createContext("/api/remove", ex -> api(ex, WebApp::removeAppliance));
        server.createContext("/api/clear", ex -> api(ex, p -> clearAll()));
        server.createContext("/api/whatif", ex -> api(ex, WebApp::whatIf));
        server.createContext("/api/tariff", ex -> api(ex, WebApp::setTariff));
        server.createContext("/api/tariff/reset", ex -> api(ex, p -> resetTariff()));
        server.createContext("/api/report", WebApp::report);
        server.start();

        String url = "http://localhost:" + port + "/";
        System.out.println("Electricity calculator is running at " + url);
        System.out.println("Press the red Stop button in Eclipse to quit.");
        openBrowser(url);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception e) {
            // fall through to the message below
        }
        System.out.println("Open this address in your browser: " + url);
    }

    // ---------- actions ----------

    private static String addAppliance(Map<String, String> p) {
        String name = p.getOrDefault("name", "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Enter a name for the appliance");
        if (name.length() > 40) throw new IllegalArgumentException("The name can have at most 40 characters");
        double watts = number(p, "watts", "Power (watts)", 1, 10000);
        double hours = number(p, "hours", "Hours per day", 0.1, 24);
        int quantity = whole(p, "quantity", "Quantity", 1, 100);

        calc.addAppliance(new Appliance(name, watts, hours, quantity));
        lastWhatIf = null;   // the old what-if no longer matches the list
        return stateJson();
    }

    private static String removeAppliance(Map<String, String> p) {
        calc.removeAppliance(whole(p, "index", "Appliance", 0, 100000));
        lastWhatIf = null;
        return stateJson();
    }

    private static String clearAll() {
        while (!calc.isEmpty()) calc.removeAppliance(0);
        lastWhatIf = null;
        return stateJson();
    }

    private static String whatIf(Map<String, String> p) {
        int index = whole(p, "index", "Appliance", 0, 100000);
        double hours = number(p, "hours", "Hours per day", 0.1, 24);
        int quantity = whole(p, "quantity", "Quantity", 1, 100);

        WhatIfResult r = new SavingsAnalyzer(calc).whatIf(index, hours, quantity);
        Appliance a = calc.getAppliances().get(index);

        lastWhatIf = r;   // kept so the downloaded report can include it
        lastWhatIfText = a.getName() + " used " + fmt(hours) + " h/day, quantity " + quantity;

        return "{\"name\":" + quote(a.getName())
                + ",\"hours\":" + num(hours)
                + ",\"quantity\":" + quantity
                + ",\"oldUnits\":" + num(r.getOldUnits())
                + ",\"oldBill\":" + num(r.getOldBill())
                + ",\"newUnits\":" + num(r.getNewUnits())
                + ",\"newBill\":" + num(r.getNewBill())
                + ",\"savingMonth\":" + num(r.savingPerMonth())
                + ",\"savingYear\":" + num(r.savingPerYear()) + "}";
    }

    // limits = "100,200" (all slabs except the last), rates = "4,6,8" (one per slab)
    private static String setTariff(Map<String, String> p) {
        String[] limitParts = splitList(p.get("limits"));
        String[] rateParts = splitList(p.get("rates"));
        if (rateParts.length == 0 || rateParts.length != limitParts.length + 1)
            throw new IllegalArgumentException("Every slab needs a rate, and the last slab has no limit");
        if (rateParts.length > 8)
            throw new IllegalArgumentException("Use at most 8 slabs");

        double[] limits = new double[rateParts.length];
        double[] rates = new double[rateParts.length];
        for (int i = 0; i < limitParts.length; i++) {
            limits[i] = parse(limitParts[i], "Limit of slab " + (i + 1), 0.1, 100000);
        }
        limits[limits.length - 1] = Double.MAX_VALUE;   // the last slab has no upper limit
        for (int i = 0; i < rateParts.length; i++) {
            rates[i] = parse(rateParts[i], "Rate of slab " + (i + 1), 0, 100);
        }
        String fixedText = p.getOrDefault("fixed", "").trim();
        double fixed = fixedText.isEmpty() ? 0 : parse(fixedText, "Fixed charge", 0, 100000);

        calc.setTariff(new Tariff(limits, rates, fixed));
        lastWhatIf = null;   // bills changed
        return stateJson();
    }

    private static String resetTariff() {
        calc.setTariff(new Tariff());
        lastWhatIf = null;
        return stateJson();
    }

    private static void report(HttpExchange ex) throws IOException {
        if (calc.isEmpty()) {
            send(ex, 400, "text/plain; charset=utf-8", "Add at least one appliance first.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        ReportWriter writer = new ReportWriter(calc, new SavingsAnalyzer(calc));
        if (lastWhatIf != null) writer.setWhatIf(lastWhatIfText, lastWhatIf);
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"electricity-report.txt\"");
        send(ex, 200, "text/plain; charset=utf-8", writer.buildReport().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- everything the page needs, as JSON ----------

    private static String stateJson() {
        SavingsAnalyzer analyzer = new SavingsAnalyzer(calc);
        StringBuilder sb = new StringBuilder("{\"appliances\":[");

        List<Appliance> list = calc.getAppliances();
        for (int i = 0; i < list.size(); i++) {
            Appliance a = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"index\":").append(i)
              .append(",\"name\":").append(quote(a.getName()))
              .append(",\"watts\":").append(num(a.getWatts()))
              .append(",\"hours\":").append(num(a.getHoursPerDay()))
              .append(",\"quantity\":").append(a.getQuantity())
              .append(",\"units\":").append(num(a.monthlyKwh()))
              .append(",\"share\":").append(num(analyzer.shareOf(a))).append("}");
        }
        sb.append("],\"totalUnits\":").append(num(calc.totalUnits()));
        sb.append(",\"totalBill\":").append(num(calc.totalBill()));

        Tariff t = calc.getTariff();
        double[] limits = t.getSlabLimits();
        double[] rates = t.getRates();
        sb.append(",\"tariff\":{\"slabs\":[");
        double previous = 0;
        for (int i = 0; i < limits.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"from\":").append(num(previous))
              .append(",\"to\":").append(limits[i] == Double.MAX_VALUE ? "null" : num(limits[i]))
              .append(",\"rate\":").append(num(rates[i])).append("}");
            previous = limits[i];
        }
        sb.append("],\"fixedCharge\":").append(num(t.getFixedCharge())).append("}");

        sb.append(",\"suggestions\":[");
        List<Suggestion> tips = analyzer.suggestions();
        for (int i = 0; i < tips.size(); i++) {
            Suggestion s = tips.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"text\":").append(quote(s.getText()))
              .append(",\"units\":").append(num(s.getUnitsSaved()))
              .append(",\"perMonth\":").append(num(s.getRupeesSaved()))
              .append(",\"perYear\":").append(num(s.getRupeesSavedPerYear())).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- HTTP plumbing ----------

    // Runs one API action and sends back JSON; a bad input becomes {"error": "..."} with status 400
    private static void api(HttpExchange ex, Action action) throws IOException {
        int status = 200;
        String body;
        try {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            body = action.run(parseForm(form));
        } catch (IllegalArgumentException e) {
            status = 400;
            body = "{\"error\":" + quote(e.getMessage()) + "}";
        } catch (Exception e) {
            status = 500;
            body = "{\"error\":" + quote("Something went wrong in the program") + "}";
            e.printStackTrace();
        }
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    // Sends files from the web folder (index.html, style.css, app.js)
    private static void serveFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file;
        try {
            file = WEB_DIR.resolve(path.substring(1)).normalize();
        } catch (InvalidPathException e) {
            file = null;
        }
        // the startsWith check stops requests like /../secret.txt from leaving the web folder
        if (file == null || !file.startsWith(WEB_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(ex, 200, contentType(file.getFileName().toString()), Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        Headers headers = ex.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-store");   // so edits to the web files show on refresh
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ---------- small helpers ----------

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String[] splitList(String text) {
        if (text == null || text.trim().isEmpty()) return new String[0];
        return text.split(",", -1);
    }

    private static double parse(String text, String label, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number");
        }
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(label + " must be between " + fmt(min) + " and " + fmt(max));
        return value;
    }

    private static double number(Map<String, String> p, String key, String label, double min, double max) {
        return parse(p.get(key), label, min, max);
    }

    private static int whole(Map<String, String> p, String key, String label, int min, int max) {
        double value = number(p, key, label, min, max);
        if (value != Math.rint(value))
            throw new IllegalArgumentException(label + " must be a whole number");
        return (int) value;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // numbers in JSON: never NaN or Infinity
    private static String num(double d) {
        return Double.isFinite(d) ? Double.toString(d) : "0";
    }

    // text in JSON: escape quotes, backslashes and control characters
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}