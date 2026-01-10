package routing;

import java.util.List;

public record RouteResult(String from, String to, List<String> path, int totalCost) {}

