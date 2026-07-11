import 'dart:convert';
import 'dart:io';

import 'package:obtainium/providers/apps_provider.dart';

class HeadlessResult {
  final String action;
  final bool success;
  final String message;
  final int? count;
  final DateTime timestamp;

  HeadlessResult({
    required this.action,
    required this.success,
    required this.message,
    this.count,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  Map<String, dynamic> toJson() => {
        'action': action,
        'success': success,
        'message': message,
        if (count != null) 'count': count,
        'timestamp': timestamp.toIso8601String(),
      };

  static Future<void> write({
    required String action,
    required bool success,
    required String message,
    int? count,
  }) async {
    try {
      final dir = await getAppStorageDir();
      final result = HeadlessResult(
        action: action,
        success: success,
        message: message,
        count: count,
      );
      final file = File('${dir.path}/headless_result.json');
      await file.writeAsString(jsonEncode(result.toJson()));
    } catch (_) {
      // Best-effort: if we can't write, the orchestrator will see no file
      // and know the previous run left no result.
    }
  }
}
