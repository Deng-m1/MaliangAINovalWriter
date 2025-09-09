import 'package:flutter/material.dart';
import 'package:ainoval/utils/web_theme.dart';

/// 小说列表加载状态组件
class LoadingView extends StatelessWidget {
  const LoadingView({
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 40,
            height: 40,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              color: WebTheme.getTextColor(context),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            '加载您的小说库...',
            style: TextStyle(
              fontSize: 14,
              color: WebTheme.getSecondaryTextColor(context),
            ),
          ),
        ],
      ),
    );
  }
}
