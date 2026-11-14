import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app_colors.dart';

/// Cifras alineadas en columna para importes y referencias.
const cifrasTabulares = [FontFeature.tabularFigures()];

/// Cambio de pantalla sin animación.
class _SinTransicion extends PageTransitionsBuilder {
  const _SinTransicion();

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => child;
}

class AppTheme {
  const AppTheme._();

  static const radio = 8.0;

  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.marca,
      brightness: Brightness.light,
    ).copyWith(
      primary: AppColors.marca,
      onPrimary: Colors.white,
      primaryContainer: AppColors.marcaTenue,
      onPrimaryContainer: AppColors.marcaOscura,
      surface: AppColors.superficie,
      onSurface: AppColors.tinta,
      onSurfaceVariant: AppColors.tintaSuave,
      outline: AppColors.linea,
      outlineVariant: AppColors.lineaSuave,
      error: AppColors.rechazo,
      onError: Colors.white,
      errorContainer: AppColors.rechazoFondo,
      onErrorContainer: AppColors.rechazo,
    );

    final base = ThemeData(brightness: Brightness.light, colorScheme: scheme);
    TextStyle fuente(double size, FontWeight peso,
            {double? height, double? spacing, Color color = AppColors.tinta}) =>
        GoogleFonts.schibstedGrotesk(
          fontSize: size,
          fontWeight: peso,
          height: height,
          letterSpacing: spacing,
          color: color,
        );

    final textTheme = GoogleFonts.schibstedGroteskTextTheme(base.textTheme)
        .apply(bodyColor: AppColors.tinta, displayColor: AppColors.tinta)
        .copyWith(
          headlineMedium: fuente(28, FontWeight.w700, height: 1.15, spacing: -0.4),
          titleLarge: fuente(20, FontWeight.w700, height: 1.25, spacing: -0.2),
          titleMedium: fuente(16, FontWeight.w600, height: 1.3),
          bodyLarge: fuente(16, FontWeight.w400, height: 1.45),
          bodyMedium: fuente(15, FontWeight.w400, height: 1.45),
          bodySmall: fuente(13, FontWeight.w400, height: 1.4, color: AppColors.tintaSuave),
          labelLarge: fuente(15, FontWeight.w600),
        );

    OutlineInputBorder borde(Color color, [double ancho = 1]) =>
        OutlineInputBorder(
          borderRadius: BorderRadius.circular(radio),
          borderSide: BorderSide(color: color, width: ancho),
        );

    final forma = RoundedRectangleBorder(borderRadius: BorderRadius.circular(radio));

    return base.copyWith(
      scaffoldBackgroundColor: AppColors.papel,
      textTheme: textTheme,
      splashFactory: NoSplash.splashFactory,
      highlightColor: Colors.transparent,
      hoverColor: AppColors.tinta.withValues(alpha: 0.04),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: _SinTransicion(),
          TargetPlatform.iOS: _SinTransicion(),
          TargetPlatform.windows: _SinTransicion(),
          TargetPlatform.macOS: _SinTransicion(),
          TargetPlatform.linux: _SinTransicion(),
        },
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.lineaSuave,
        thickness: 1,
        space: 1,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.superficie,
        foregroundColor: AppColors.tinta,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge,
        shape: const Border(bottom: BorderSide(color: AppColors.lineaSuave)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.superficie,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        border: borde(AppColors.linea),
        enabledBorder: borde(AppColors.linea),
        focusedBorder: borde(AppColors.marca, 2),
        errorBorder: borde(AppColors.rechazo),
        focusedErrorBorder: borde(AppColors.rechazo, 2),
        labelStyle: textTheme.bodyMedium?.copyWith(color: AppColors.tintaSuave),
        helperStyle: textTheme.bodySmall,
        errorStyle: textTheme.bodySmall?.copyWith(color: AppColors.rechazo),
        errorMaxLines: 3,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.marca,
          foregroundColor: Colors.white,
          disabledBackgroundColor: AppColors.lineaSuave,
          disabledForegroundColor: AppColors.tintaSuave,
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20),
          shape: forma,
          textStyle: textTheme.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.tinta,
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20),
          side: const BorderSide(color: AppColors.linea),
          shape: forma,
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.marca,
          minimumSize: const Size(48, 44),
          shape: forma,
          textStyle: textTheme.labelLarge,
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.superficie,
        selectedColor: AppColors.marcaTenue,
        side: const BorderSide(color: AppColors.linea),
        shape: forma,
        labelStyle: textTheme.bodyMedium,
        showCheckmark: false,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.superficie,
        indicatorColor: AppColors.marcaTenue,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        height: 64,
        labelTextStyle: WidgetStatePropertyAll(
          textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AppColors.superficie,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: textTheme.titleLarge,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: AppColors.tinta,
        contentTextStyle: textTheme.bodyMedium?.copyWith(color: Colors.white),
        shape: forma,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.marca,
      ),
    );
  }
}
