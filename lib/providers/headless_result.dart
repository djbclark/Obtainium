import 'dart:convert';
import 'dart:io';

import 'package:android_intent_plus/android_intent.dart';
import 'package:obtainium/providers/apps_provider.dart';

class HeadlessResult {
  final String action;
  final bool success;
  final String message;
  final int? count;
  final int? updatedCount;
  final int? failedCount;
  final int? skippedCount;
  final DateTime timestamp;

  HeadlessResult({
    required this.action,
    required this.success,
    required this.message,
    this.count,
    this.updatedCount,
    this.failedCount,
    this.skippedCount,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  Map<String, dynamic> toJson() => {
        'action': action,
        'success': success,
        'message': message,
        if (count != null) 'count': count,
        if (updatedCount != null) 'updatedCount': updatedCount,
        if (failedCount != null) 'failedCount': failedCount,
        if (skippedCount != null) 'skippedCount': skippedCount,
        'timestamp': timestamp.toIso8601String(),
      };

  static Future<void> write({
    required String action,
    required bool success,
    required String message,
    int? count,
    int? updatedCount,
    int? failedCount,
    int? skippedCount,
  }) async {
    final result = HeadlessResult(
      action: action,
      success: success,
      message: message,
      count: count,
      updatedCount: updatedCount,
      failedCount: failedCount,
      skippedCount: skippedCount,
    );

    // Write headless_result.json for ADB/shell polling access.
    try {
      final dir = await getAppStorageDir();
      final file = File('${dir.path}/headless_result.json');
      await file.writeAsString(jsonEncode(result.toJson()));
    } catch (_) {}

    // Send broadcast intent for real-time fleet orchestration.
    try {
      await AndroidIntent(
        action: 'dev.imranr.obtainium.action.HEADLESS_RESULT',
        arguments: {
          'action': action,
          'success': success ? 'true' : 'false',
          'message': message,
          if (count != null) 'count': count.toString(),
          if (updatedCount != null) 'updatedCount': updatedCount.toString(),
          if (failedCount != null) 'failedCount': failedCount.toString(),
          if (skippedCount != null) 'skippedCount': skippedCount.toString(),
        },
      ).sendBroadcast();
    } catch (_) {}
  }
}
