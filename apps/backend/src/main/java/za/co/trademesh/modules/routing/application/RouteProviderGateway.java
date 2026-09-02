package za.co.trademesh.modules.routing.application;

public interface RouteProviderGateway {

    ResolvedRoutes resolve(RouteProvider.ProviderRequest request) throws RouteProviderException;

    record ResolvedRoutes(RouteProvider.ProviderResult providerResult, boolean fallbackUsed, String fallbackReason) {}
}
