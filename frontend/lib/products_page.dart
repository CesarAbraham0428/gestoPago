import 'package:flutter/material.dart';

import 'auth_service.dart';
import 'login_page.dart';

class ProductsPage extends StatefulWidget {
  const ProductsPage({
    super.key,
    required this.session,
    required this.authService,
  });

  final AppSession session;
  final AuthService authService;

  @override
  State<ProductsPage> createState() => _ProductsPageState();
}

class _ProductsPageState extends State<ProductsPage> {
  ProductsResult? _result;
  Object? _error;
  bool _loading = true;
  String _filter = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.authService.loadProducts(widget.session);
      if (!mounted) return;
      setState(() {
        _result = result;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  void _logout() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(
        builder: (_) => LoginPage(authService: widget.authService),
      ),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final products = (_result?.products ?? const <Product>[])
        .where(
          (product) =>
              product.name.toLowerCase().contains(_filter.toLowerCase()) ||
              product.service.toLowerCase().contains(_filter.toLowerCase()),
        )
        .toList(growable: false);

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        titleSpacing: 28,
        title: Row(
          children: [
            Icon(Icons.account_balance_wallet_rounded, color: colors.primary),
            const SizedBox(width: 10),
            const Text(
              'GestoPago',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Center(
              child: Text(
                widget.session.displayName,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: _logout,
            icon: const Icon(Icons.logout_rounded),
          ),
          const SizedBox(width: 18),
        ],
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1120),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(28, 32, 28, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Catálogo de productos',
                  style: Theme.of(context).textTheme.headlineMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Text(
                  'Consulta los productos disponibles en GestoPago.',
                  style: Theme.of(context).textTheme.bodyLarge
                      ?.copyWith(color: colors.onSurfaceVariant),
                ),
                const SizedBox(height: 22),
                if (_result case final result?) _connectionBanner(result),
                const SizedBox(height: 20),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        onChanged: (value) =>
                            setState(() => _filter = value.trim()),
                        decoration: const InputDecoration(
                          hintText: 'Buscar por producto o servicio',
                          prefixIcon: Icon(Icons.search_rounded),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    IconButton.filledTonal(
                      tooltip: 'Actualizar catálogo',
                      onPressed: _loading ? null : _load,
                      icon: const Icon(Icons.refresh_rounded),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Expanded(child: _content(products)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _connectionBanner(ProductsResult result) {
    final color = result.offline
        ? const Color(0xFF8A5A00)
        : const Color(0xFF23744F);
    final background = result.offline
        ? const Color(0xFFFFF4D9)
        : const Color(0xFFE7F6EE);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Icon(
            result.offline ? Icons.cloud_off_rounded : Icons.cloud_done_rounded,
            color: color,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(result.message, style: TextStyle(color: color)),
          ),
          Text(
            '${result.products.length} productos',
            style: TextStyle(color: color, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }

  Widget _content(List<Product> products) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return _emptyState(
        icon: Icons.cloud_off_rounded,
        title: 'No se pudo cargar el catálogo',
        details: '$_error',
        action: FilledButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Intentar de nuevo'),
        ),
      );
    }
    if (products.isEmpty) {
      return _emptyState(
        icon: Icons.inventory_2_outlined,
        title: _filter.isEmpty
            ? 'Aún no hay productos guardados'
            : 'No encontramos coincidencias',
        details: _filter.isEmpty
            ? 'Conéctate a la API para cargar el catálogo. Después podrás verlo sin conexión.'
            : 'Prueba con otro nombre de producto o servicio.',
        action: _filter.isEmpty
            ? FilledButton.icon(
                onPressed: _load,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Actualizar catálogo'),
              )
            : const SizedBox.shrink(),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth > 820
            ? 3
            : constraints.maxWidth > 540
            ? 2
            : 1;
        return GridView.builder(
          itemCount: products.length,
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisSpacing: 14,
            crossAxisSpacing: 14,
            childAspectRatio: columns == 1 ? 3.1 : 1.45,
          ),
          itemBuilder: (context, index) => _productCard(products[index]),
        );
      },
    );
  }

  Widget _emptyState({
    required IconData icon,
    required String title,
    required String details,
    required Widget action,
  }) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 460),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 16),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleLarge
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            Text(
              details,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 20),
            action,
          ],
        ),
      ),
    );
  }

  Widget _productCard(Product product) {
    final colors = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: colors.primaryContainer,
                borderRadius: BorderRadius.circular(13),
              ),
              child: Icon(
                Icons.receipt_long_rounded,
                color: colors.onPrimaryContainer,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  if (product.service.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      product.service,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall
                          ?.copyWith(color: colors.onSurfaceVariant),
                    ),
                  ],
                  if (product.productId != null) ...[
                    const SizedBox(height: 5),
                    Text(
                      'ID ${product.productId}',
                      style: Theme.of(context).textTheme.labelSmall
                          ?.copyWith(color: colors.onSurfaceVariant),
                    ),
                  ],
                ],
              ),
            ),
            if (product.price != null) ...[
              const SizedBox(width: 10),
              Text(
                '\$${product.price!.toStringAsFixed(2)}',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: colors.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
