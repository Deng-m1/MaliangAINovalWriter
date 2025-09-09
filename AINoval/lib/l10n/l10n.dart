import 'package:flutter/material.dart';
import 'package:ainoval/l10n/app_localizations.dart';

extension AppLocalizationsX on BuildContext {
  AppLocalizations get l10n => AppLocalizations.of(this)!;
}

class L10n {
  static const all = [
    Locale('zh', 'CN'),
    Locale('en', 'US'),
  ];
} 