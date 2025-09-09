import 'package:ainoval/models/novel_summary.dart';
import 'package:ainoval/utils/date_formatter.dart';
import 'package:ainoval/utils/web_theme.dart';
import 'package:flutter/material.dart';
import 'package:ainoval/screens/editor/widgets/novel_settings_view.dart';
import 'package:ainoval/services/api_service/repositories/editor_repository.dart';
import 'package:ainoval/services/api_service/repositories/storage_repository.dart';
// unused import removed
import 'package:ainoval/blocs/novel_list/novel_list_bloc.dart';
import 'package:ainoval/utils/logger.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';
import 'package:ainoval/services/novel_file_service.dart'; // 导入小说文件服务
import 'package:ainoval/widgets/common/top_toast.dart';

class NovelCard extends StatefulWidget {
  const NovelCard({
    super.key,
    required this.novel,
    required this.onTap,
    required this.isGridView,
  });
  final NovelSummary novel;
  final VoidCallback onTap;
  final bool isGridView;

  @override
  State<NovelCard> createState() => _NovelCardState();
}

class _NovelCardState extends State<NovelCard> {
  bool _isHovering = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _isHovering = true),
      onExit: (_) => setState(() => _isHovering = false),
      child:
          widget.isGridView ? _buildGridCard(context) : _buildListCard(context),
    );
  }

  // 构建网格视图中的卡片 - 优化设计
  Widget _buildGridCard(BuildContext context) {
    final isDark = WebTheme.isDarkMode(context);
    final bgColor = NovelCardDesignUtils.getRandomPastelColor(widget.novel.id, null, isDark);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      transform: _isHovering
          ? (Matrix4.identity()..translate(0, -4))
          : Matrix4.identity(),
      child: Card(
        clipBehavior: Clip.antiAlias,
        elevation: _isHovering ? 6.0 : 2.0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: _isHovering
              ? BorderSide(
                  color: WebTheme.getTextColor(context).withOpacity(0.5), width: 1.5)
              : BorderSide.none,
        ),
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(12),
          hoverColor: WebTheme.getTextColor(context).withOpacity(0.02),
          splashColor: WebTheme.getTextColor(context).withOpacity(0.1),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              // 封面区域
              Flexible(
                child: AspectRatio(
                  aspectRatio: 3 / 4,
                  child: NovelCoverWidget(
                    novel: widget.novel,
                    bgColor: bgColor,
                  ),
                ),
              ),

              // 信息区域
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: WebTheme.getSurfaceColor(context),
                  boxShadow: _isHovering
                      ? [
                          BoxShadow(
                              color: WebTheme.getTextColor(context).withOpacity(0.05),
                              blurRadius: 4,
                              offset: const Offset(0, -2))
                        ]
                      : null,
                ),
                child: NovelInfoWidget(
                  novel: widget.novel,
                  isCompact: true,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 构建列表视图中的卡片 - 优化设计
  Widget _buildListCard(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = WebTheme.isDarkMode(context);
    final bgColor = NovelCardDesignUtils.getRandomPastelColor(widget.novel.id, null, isDark);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: _isHovering ? 3.0 : 1.0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: _isHovering
            ? BorderSide(
                color: theme.colorScheme.primary.withOpacity(0.5), width: 1)
            : BorderSide(color: theme.colorScheme.outline, width: 0.5),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        hoverColor: theme.colorScheme.onSurface.withOpacity(0.03),
        splashColor: theme.colorScheme.primary.withOpacity(0.1),
        child: Container(
          height: 120, // 固定卡片高度
          child: Row(
            children: [
              // 封面图 - 增大尺寸
              Container(
                width: 80,
                height: 120,
                decoration: BoxDecoration(
                  color: bgColor,
                  boxShadow: [
                    BoxShadow(
                      color: theme.colorScheme.onSurface.withOpacity(0.1),
                      blurRadius: 2,
                      offset: const Offset(0, 1),
                    ),
                  ],
                ),
                child: Stack(
                  children: [
                    SizedBox.expand(
                      child: widget.novel.coverUrl.isNotEmpty
                          ? Image.network(
                              widget.novel.coverUrl,
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) {
                                return NovelCoverDesign(
                                    bgColor: bgColor, id: widget.novel.id);
                              },
                            )
                          : NovelCoverDesign(
                              bgColor: bgColor, id: widget.novel.id),
                    ),
                    // 完成度进度条
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: LinearProgressIndicator(
                        value: widget.novel.completionPercentage,
                        backgroundColor: theme.colorScheme.onSurface.withOpacity(0.12),
                        color: theme.colorScheme.primary.withOpacity(0.7),
                        minHeight: 4,
                      ),
                    ),
                  ],
                ),
              ),

              // 信息区域 - 移除左侧间距，让它紧贴封面
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                                  child: NovelInfoWidget(
                  novel: widget.novel,
                  isCompact: false,
                ),
                ),
              ),

              // 操作按钮 - 增加右侧padding
              Padding(
                padding: const EdgeInsets.only(right: 16),
                child: NovelActionsMenu(novel: widget.novel),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 小说封面组件
class NovelCoverWidget extends StatelessWidget {
  const NovelCoverWidget({
    super.key,
    required this.novel,
    required this.bgColor,
  });

  final NovelSummary novel;
  final Color bgColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: bgColor,
        gradient: LinearGradient(
          colors: [
            bgColor.withOpacity(0.9),
            bgColor,
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          // 优先显示封面图片（如果有coverUrl）
          if (novel.coverUrl.isNotEmpty)
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: Image.network(
                novel.coverUrl,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) {
                  // 图片加载失败时显示默认设计
                  return NovelCoverDesign(bgColor: bgColor, id: novel.id);
                },
                loadingBuilder: (context, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return Center(
                    child: CircularProgressIndicator(
                      value: loadingProgress.expectedTotalBytes != null
                          ? loadingProgress.cumulativeBytesLoaded /
                              loadingProgress.expectedTotalBytes!
                          : null,
                      strokeWidth: 2,
                    ),
                  );
                },
              ),
            )
          else
            // 如果没有封面URL，则使用默认设计
            NovelCoverDesign(bgColor: bgColor, id: novel.id),

          // 显示完成进度条
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: LinearProgressIndicator(
              value: novel.completionPercentage,
              backgroundColor: theme.colorScheme.onSurface.withOpacity(0.12),
              color: theme.colorScheme.primary.withOpacity(0.7),
              minHeight: 3,
            ),
          ),

          // 左上角显示字数指示
          if (novel.wordCount > 0)
            Positioned(
              top: 8,
              left: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                decoration: BoxDecoration(
                  color: theme.colorScheme.onSurface.withOpacity(0.4),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  '${novel.wordCount}字',
                  style: TextStyle(
                    color: theme.colorScheme.surface,
                    fontSize: 10,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ),
            
          // 右上角添加三点水按钮
          Positioned(
            top: 8,
            right: 8,
            child: PopupMenuButton<String>(
              icon: Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: theme.colorScheme.onSurface.withOpacity(0.6),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.more_vert,
                  color: theme.colorScheme.surface,
                  size: 16,
                ),
              ),
              padding: EdgeInsets.zero,
              splashRadius: 20,
              elevation: 4,
              onSelected: (String result) => _handleMenuAction(context, result),
              itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
                const PopupMenuItem<String>(
                  value: 'metadata',
                  child: ListTile(
                    leading: Icon(Icons.info_outline, size: 18),
                    title: Text('查看元数据', style: TextStyle(fontSize: 14)),
                    contentPadding: EdgeInsets.symmetric(horizontal: 8),
                    dense: true,
                  ),
                ),
                const PopupMenuItem<String>(
                  value: 'export',
                  child: ListTile(
                    leading: Icon(Icons.file_download_outlined, size: 18),
                    title: Text('导出小说', style: TextStyle(fontSize: 14)),
                    contentPadding: EdgeInsets.symmetric(horizontal: 8),
                    dense: true,
                  ),
                ),
                const PopupMenuDivider(),
                PopupMenuItem<String>(
                  value: 'delete',
                  child: ListTile(
                    leading: Icon(
                      Icons.delete_outline,
                      size: 18,
                      color: WebTheme.error,
                    ),
                    title: Text(
                      '删除小说',
                      style: TextStyle(fontSize: 14, color: WebTheme.error),
                    ),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 8),
                    dense: true,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  // 处理菜单选项点击
  void _handleMenuAction(BuildContext context, String action) async {
    final theme = Theme.of(context);
    
    switch (action) {
      case 'metadata':
        _navigateToMetadataSettings(context);
        break;
      case 'export':
        _exportNovel(context);
        break;
      case 'delete':
        _showDeleteConfirmDialog(context);
        break;
    }
  }
  
  // 跳转到元数据设置页面
  void _navigateToMetadataSettings(BuildContext context) {
    // 获取必要的repository实例
    final editorRepository = context.read<EditorRepository>();
    final storageRepository = context.read<StorageRepository>();
    
    // 跳转到编辑器界面并打开设置页面
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => MultiRepositoryProvider(
          providers: [
            RepositoryProvider<EditorRepository>.value(value: editorRepository),
            RepositoryProvider<StorageRepository>.value(value: storageRepository),
          ],
          child: Scaffold(
            appBar: AppBar(
              title: Text(novel.title),
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => Navigator.pop(context),
              ),
            ),
            body: NovelSettingsView(
              novel: novel,
              onSettingsClose: () => Navigator.pop(context),
            ),
          ),
        ),
      ),
    ).then((value) {
      // 返回后刷新列表
      context.read<NovelListBloc>().add(LoadNovels());
    });
  }
  
  // 显示删除确认对话框
  void _showDeleteConfirmDialog(BuildContext context) async {
    final theme = Theme.of(context);
    final novelTitle = novel.title;
    final TextEditingController confirmController = TextEditingController();
    bool isConfirmed = false;
    
    final confirmedResult = await showDialog<bool?>(
      context: context,
      barrierDismissible: false,
      builder: (context) {
         return StatefulBuilder(
          builder: (context, setDialogState) {
             return AlertDialog(
               title: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: WebTheme.error),
                  const SizedBox(width: 8),
                  const Text('永久删除'), 
                ],
              ),
              content: SingleChildScrollView(
                child: ListBody(
                  children: <Widget>[
                     const Text(
                      '警告：此操作无法撤销!',
                      style: TextStyle(fontWeight: FontWeight.bold, color: WebTheme.error),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '删除这本小说将永久移除其所有内容、章节和设置。这些数据将无法恢复。',
                    ),
                    const SizedBox(height: 16),
                    RichText(
                       text: TextSpan(
                         style: DefaultTextStyle.of(context).style,
                         children: <TextSpan>[
                           const TextSpan(text: '请输入小说标题 '),
                           TextSpan(text: '"$novelTitle"', style: const TextStyle(fontWeight: FontWeight.bold)),
                           const TextSpan(text: ' 以确认删除:'),
                         ],
                       ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: confirmController,
                      decoration: InputDecoration(
                        border: const OutlineInputBorder(),
                        hintText: '输入 "$novelTitle"',
                        errorText: !isConfirmed && confirmController.text.isNotEmpty && confirmController.text != novelTitle 
                          ? '标题不匹配'
                          : null,
                      ),
                      autofocus: true,
                       onChanged: (value) {
                         setDialogState(() {
                           isConfirmed = value == novelTitle;
                         });
                      },
                    ),
                  ],
                ),
              ),
              actions: <Widget>[
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('取消'),
                ),
                ElevatedButton(
                  onPressed: isConfirmed ? () {
                     Navigator.pop(context, true);
                  } : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: theme.colorScheme.error,
                    foregroundColor: theme.colorScheme.onError,
                     disabledBackgroundColor: theme.colorScheme.outline.withOpacity(0.3),
                  ),
                  child: const Text('确认删除'),
                ),
              ],
            );
          }
        );
      }
    );
    
    confirmController.dispose();
    if (confirmedResult == true) {
      _deleteNovel(context);
    }
  }
  
  // 删除小说
  Future<void> _deleteNovel(BuildContext context) async {
    try {
      final repository = context.read<EditorRepository>();
      await repository.deleteNovel(novelId: novel.id);
      
      AppLogger.i('NovelCard', '删除小说成功: ${novel.id}');
      
      TopToast.success(context, '小说已永久删除。');
      
      // 刷新小说列表
      context.read<NovelListBloc>().add(LoadNovels());
    } catch (e, stackTrace) {
      AppLogger.e('NovelCard', '删除小说失败', e, stackTrace);
      final errorMessage = e is Exception ? e.toString().replaceFirst('Exception: ', '') : e.toString();
      TopToast.error(context, '删除失败: $errorMessage');
    }
  }

  // 导出小说
  void _exportNovel(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('选择导出格式'),
          content: const Text('将小说保存到本地设备'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.txt);
              },
              child: const Text('TXT 文本'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.markdown);
              },
              child: const Text('Markdown'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.json);
              },
              child: const Text('JSON'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performMultipleExport(context);
              },
              child: const Text('所有格式'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消'),
            ),
          ],
        );
      },
    );
  }
  
  /// 执行单格式导出
  Future<void> _performExport(BuildContext context, NovelExportFormat format) async {
    try {
      _showLoadingDialog(context, '正在导出小说...');
      
      final novelFileService = context.read<NovelFileService>();
      final result = await novelFileService.exportNovelToFile(
        novel.id,
        format: format,
      );
      
      _hideLoadingDialog(context);
      _showExportSuccessDialog(context, result);
      
    } catch (e) {
      _hideLoadingDialog(context);
      _showErrorDialog(context, '导出失败', e.toString());
      AppLogger.e('NovelCard', '导出小说失败', e);
    }
  }
  
  /// 执行多格式导出
  Future<void> _performMultipleExport(BuildContext context) async {
    try {
      _showLoadingDialog(context, '正在导出所有格式...');
      
      final novelFileService = context.read<NovelFileService>();
      final results = await novelFileService.exportNovelMultipleFormats(novel.id);
      
      _hideLoadingDialog(context);
      
      if (results.isNotEmpty) {
        _showMultipleExportSuccessDialog(context, results);
      } else {
        _showErrorDialog(context, '导出失败', '没有成功导出任何格式');
      }
      
    } catch (e) {
      _hideLoadingDialog(context);
      _showErrorDialog(context, '导出失败', e.toString());
      AppLogger.e('NovelCard', '批量导出小说失败', e);
    }
  }
  
  /// 显示加载对话框
  void _showLoadingDialog(BuildContext context, String message) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          content: Row(
            children: [
              const CircularProgressIndicator(),
              const SizedBox(width: 20),
              Text(message),
            ],
          ),
        );
      },
    );
  }
  
  /// 隐藏加载对话框
  void _hideLoadingDialog(BuildContext context) {
    Navigator.of(context).pop();
  }
  
  /// 显示导出成功对话框
  void _showExportSuccessDialog(BuildContext context, NovelExportResult result) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('导出成功'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('文件：${result.fileName}'),
              Text('大小：${(result.fileSizeBytes / 1024).toStringAsFixed(1)} KB'),
              Text('格式：${result.format.name.toUpperCase()}'),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () async {
                Navigator.pop(context);
                try {
                  final novelFileService = context.read<NovelFileService>();
                  await novelFileService.shareExportedFile(result);
                } catch (e) {
                  _showErrorDialog(context, '分享失败', e.toString());
                }
              },
              child: const Text('分享'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
  
  /// 显示多格式导出成功对话框
  void _showMultipleExportSuccessDialog(BuildContext context, List<NovelExportResult> results) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('导出成功'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('成功导出 ${results.length} 个文件：'),
              const SizedBox(height: 8),
              ...results.map((result) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text('• ${result.format.name.toUpperCase()}: ${result.fileName}'),
              )),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
  
  /// 显示错误对话框
  void _showErrorDialog(BuildContext context, String title, String message) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
}

/// 小说信息组件
class NovelInfoWidget extends StatelessWidget {
  const NovelInfoWidget({
    super.key,
    required this.novel,
    this.isCompact = false,
  });

  final NovelSummary novel;
  final bool isCompact;

  @override
  Widget build(BuildContext context) {
    if (isCompact) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            novel.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 14,
              color: WebTheme.getTextColor(context),
            ),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Icon(
                Icons.access_time,
                size: 12,
                color: WebTheme.getSecondaryTextColor(context),
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  DateFormatter.formatRelative(novel.lastEditTime),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 11,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          // 添加字数信息
          Row(
            children: [
              Icon(
                Icons.text_fields,
                size: 12,
                color: WebTheme.getSecondaryTextColor(context),
              ),
              const SizedBox(width: 4),
              Text(
                '${_formatWordCount(novel.wordCount)}字',
                style: TextStyle(
                  fontSize: 11,
                  color: WebTheme.getSecondaryTextColor(context),
                ),
              ),
            ],
          ),
          // 添加卷、章节、场景数量信息
          const SizedBox(height: 4),
          Row(
            children: [
              Icon(
                Icons.library_books_outlined, 
                size: 11, 
                color: WebTheme.getSecondaryTextColor(context),
              ),
              const SizedBox(width: 4),
              Text(
                '${novel.actCount}卷 / ${novel.chapterCount}章 / ${novel.sceneCount}场景',
                style: TextStyle(
                  fontSize: 10,
                  color: WebTheme.getSecondaryTextColor(context),
                ),
              ),
            ],
          ),
          if (novel.seriesName.isNotEmpty) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                Icon(
                  Icons.bookmark_border,
                  size: 12,
                  color: WebTheme.getSecondaryTextColor(context),
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    novel.seriesName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11,
                      color: WebTheme.getSecondaryTextColor(context),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      );
    } else {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            novel.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 15,
              color: WebTheme.getTextColor(context),
            ),
          ),
          if (novel.seriesName.isNotEmpty) ...[
            const SizedBox(height: 3),
            Text(
              novel.seriesName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                color: WebTheme.getSecondaryTextColor(context),
              ),
            ),
          ],
          const SizedBox(height: 6),
          Row(
            children: [
              Icon(
                Icons.access_time,
                size: 14,
                color: WebTheme.getSecondaryTextColor(context),
              ),
              const SizedBox(width: 4),
              Text(
                '上次编辑: ${DateFormatter.formatRelative(novel.lastEditTime)}',
                style: TextStyle(
                  fontSize: 11,
                  color: WebTheme.getSecondaryTextColor(context),
                ),
              ),
              const SizedBox(width: 12),
              // 美化字数显示
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: WebTheme.getTextColor(context).withOpacity(0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.text_fields,
                      size: 12,
                      color: WebTheme.getTextColor(context),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '${_formatWordCount(novel.wordCount)}字',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: WebTheme.getTextColor(context),
                      ),
                    ),
                  ],
                ),
              ),
              // 添加卷、章节、场景数量信息
              const SizedBox(width: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: WebTheme.getSecondaryTextColor(context).withOpacity(0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.library_books_outlined,
                      size: 12,
                      color: WebTheme.getSecondaryTextColor(context),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '${novel.actCount}卷 / ${novel.chapterCount}章 / ${novel.sceneCount}场景',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: WebTheme.getSecondaryTextColor(context),
                      ),
                    ),
                  ],
                ),
              ),
              // 阅读时间提示（如果阅读时间不为0）
              if (novel.readTime > 0) ...[
                const SizedBox(width: 8),
                Text(
                  '约${novel.readTime}分钟',
                  style: TextStyle(
                    fontSize: 10,
                    fontStyle: FontStyle.italic,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
              ],
            ],
          ),
        ],
      );
    }
  }
  
  // 格式化字数显示
  String _formatWordCount(int count) {
    if (count < 1000) {
      return count.toString();
    } else if (count < 10000) {
      return '${(count / 1000).toStringAsFixed(1)}K';
    } else {
      return '${(count / 10000).toStringAsFixed(1)}万';
    }
  }
}

/// 小说操作菜单
class NovelActionsMenu extends StatelessWidget {
  const NovelActionsMenu({
    super.key,
    required this.novel,
  });

  final NovelSummary novel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PopupMenuButton<String>(
      icon: Icon(
        Icons.more_vert,
        color: theme.colorScheme.onSurfaceVariant,
        size: 20,
      ),
      onSelected: (String result) {
        _handleMenuAction(context, result);
      },
      itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
        const PopupMenuItem<String>(
          value: 'metadata',
          child: ListTile(
            leading: Icon(Icons.info_outline, size: 18),
            title: Text('查看元数据', style: TextStyle(fontSize: 14)),
            contentPadding: EdgeInsets.symmetric(horizontal: 8),
            dense: true,
          ),
        ),
        const PopupMenuItem<String>(
          value: 'export',
          child: ListTile(
            leading: Icon(Icons.file_download_outlined, size: 18),
            title: Text('导出小说', style: TextStyle(fontSize: 14)),
            contentPadding: EdgeInsets.symmetric(horizontal: 8),
            dense: true,
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem<String>(
          value: 'delete',
          child: ListTile(
            leading: Icon(
              Icons.delete_outline,
              size: 18,
              color: Theme.of(context).colorScheme.error,
            ),
            title: Text(
              '删除小说',
              style: TextStyle(fontSize: 14, color: Theme.of(context).colorScheme.error),
            ),
            contentPadding: const EdgeInsets.symmetric(horizontal: 8),
            dense: true,
          ),
        ),
      ],
      splashRadius: 18,
    );
  }
  
  // 处理菜单选项点击
  void _handleMenuAction(BuildContext context, String action) async {
    final theme = Theme.of(context);
    
    switch (action) {
      case 'metadata':
        _navigateToMetadataSettings(context);
        break;
      case 'export':
        _exportNovel(context);
        break;
      case 'delete':
        _showDeleteConfirmDialog(context);
        break;
    }
  }
  
  // 跳转到元数据设置页面
  void _navigateToMetadataSettings(BuildContext context) {
    // 获取必要的repository实例
    final editorRepository = context.read<EditorRepository>();
    final storageRepository = context.read<StorageRepository>();
    
    // 跳转到编辑器界面并打开设置页面
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => MultiRepositoryProvider(
          providers: [
            RepositoryProvider<EditorRepository>.value(value: editorRepository),
            RepositoryProvider<StorageRepository>.value(value: storageRepository),
          ],
          child: Scaffold(
            appBar: AppBar(
              title: Text(novel.title),
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => Navigator.pop(context),
              ),
            ),
            body: NovelSettingsView(
              novel: novel,
              onSettingsClose: () => Navigator.pop(context),
            ),
          ),
        ),
      ),
    ).then((value) {
      // 返回后刷新列表
      context.read<NovelListBloc>().add(LoadNovels());
    });
  }
  
  // 显示删除确认对话框
  void _showDeleteConfirmDialog(BuildContext context) async {
    final theme = Theme.of(context);
    final novelTitle = novel.title;
    final TextEditingController confirmController = TextEditingController();
    bool isConfirmed = false;
    
    final confirmedResult = await showDialog<bool?>(
      context: context,
      barrierDismissible: false,
      builder: (context) {
         return StatefulBuilder(
          builder: (context, setDialogState) {
             return AlertDialog(
               title: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: WebTheme.error),
                  const SizedBox(width: 8),
                  const Text('永久删除'), 
                ],
              ),
              content: SingleChildScrollView(
                child: ListBody(
                  children: <Widget>[
                     const Text(
                      '警告：此操作无法撤销!',
                      style: TextStyle(fontWeight: FontWeight.bold, color: WebTheme.error),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '删除这本小说将永久移除其所有内容、章节和设置。这些数据将无法恢复。',
                    ),
                    const SizedBox(height: 16),
                    RichText(
                       text: TextSpan(
                         style: DefaultTextStyle.of(context).style,
                         children: <TextSpan>[
                           const TextSpan(text: '请输入小说标题 '),
                           TextSpan(text: '"$novelTitle"', style: const TextStyle(fontWeight: FontWeight.bold)),
                           const TextSpan(text: ' 以确认删除:'),
                         ],
                       ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: confirmController,
                      decoration: InputDecoration(
                        border: const OutlineInputBorder(),
                        hintText: '输入 "$novelTitle"',
                        errorText: !isConfirmed && confirmController.text.isNotEmpty && confirmController.text != novelTitle 
                          ? '标题不匹配'
                          : null,
                      ),
                      autofocus: true,
                       onChanged: (value) {
                         setDialogState(() {
                           isConfirmed = value == novelTitle;
                         });
                      },
                    ),
                  ],
                ),
              ),
              actions: <Widget>[
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('取消'),
                ),
                ElevatedButton(
                  onPressed: isConfirmed ? () {
                     Navigator.pop(context, true);
                  } : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: theme.colorScheme.error,
                    foregroundColor: theme.colorScheme.onError,
                     disabledBackgroundColor: theme.colorScheme.outline.withOpacity(0.3),
                  ),
                  child: const Text('确认删除'),
                ),
              ],
            );
          }
        );
      }
    );
    
    confirmController.dispose();
    if (confirmedResult == true) {
      _deleteNovel(context);
    }
  }
  
  // 删除小说
  Future<void> _deleteNovel(BuildContext context) async {
    try {
      final repository = context.read<EditorRepository>();
      await repository.deleteNovel(novelId: novel.id);
      
      AppLogger.i('NovelCard', '删除小说成功: ${novel.id}');
      
      TopToast.success(context, '小说已永久删除。');
      
      // 刷新小说列表
      context.read<NovelListBloc>().add(LoadNovels());
    } catch (e, stackTrace) {
      AppLogger.e('NovelCard', '删除小说失败', e, stackTrace);
      final errorMessage = e is Exception ? e.toString().replaceFirst('Exception: ', '') : e.toString();
      TopToast.error(context, '删除失败: $errorMessage');
    }
  }

  // 导出小说
  void _exportNovel(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('选择导出格式'),
          content: const Text('将小说保存到本地设备'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.txt);
              },
              child: const Text('TXT 文本'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.markdown);
              },
              child: const Text('Markdown'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performExport(context, NovelExportFormat.json);
              },
              child: const Text('JSON'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _performMultipleExport(context);
              },
              child: const Text('所有格式'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消'),
            ),
          ],
        );
      },
    );
  }
  
  /// 执行单格式导出
  Future<void> _performExport(BuildContext context, NovelExportFormat format) async {
    try {
      _showLoadingDialog(context, '正在导出小说...');
      
      final novelFileService = context.read<NovelFileService>();
      final result = await novelFileService.exportNovelToFile(
        novel.id,
        format: format,
      );
      
      _hideLoadingDialog(context);
      _showExportSuccessDialog(context, result);
      
    } catch (e) {
      _hideLoadingDialog(context);
      _showErrorDialog(context, '导出失败', e.toString());
      AppLogger.e('NovelCard', '导出小说失败', e);
    }
  }
  
  /// 执行多格式导出
  Future<void> _performMultipleExport(BuildContext context) async {
    try {
      _showLoadingDialog(context, '正在导出所有格式...');
      
      final novelFileService = context.read<NovelFileService>();
      final results = await novelFileService.exportNovelMultipleFormats(novel.id);
      
      _hideLoadingDialog(context);
      
      if (results.isNotEmpty) {
        _showMultipleExportSuccessDialog(context, results);
      } else {
        _showErrorDialog(context, '导出失败', '没有成功导出任何格式');
      }
      
    } catch (e) {
      _hideLoadingDialog(context);
      _showErrorDialog(context, '导出失败', e.toString());
      AppLogger.e('NovelCard', '批量导出小说失败', e);
    }
  }
  
  /// 显示加载对话框
  void _showLoadingDialog(BuildContext context, String message) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          content: Row(
            children: [
              const CircularProgressIndicator(),
              const SizedBox(width: 20),
              Text(message),
            ],
          ),
        );
      },
    );
  }
  
  /// 隐藏加载对话框
  void _hideLoadingDialog(BuildContext context) {
    Navigator.of(context).pop();
  }
  
  /// 显示导出成功对话框
  void _showExportSuccessDialog(BuildContext context, NovelExportResult result) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('导出成功'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('文件：${result.fileName}'),
              Text('大小：${(result.fileSizeBytes / 1024).toStringAsFixed(1)} KB'),
              Text('格式：${result.format.name.toUpperCase()}'),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () async {
                Navigator.pop(context);
                try {
                  final novelFileService = context.read<NovelFileService>();
                  await novelFileService.shareExportedFile(result);
                } catch (e) {
                  _showErrorDialog(context, '分享失败', e.toString());
                }
              },
              child: const Text('分享'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
  
  /// 显示多格式导出成功对话框
  void _showMultipleExportSuccessDialog(BuildContext context, List<NovelExportResult> results) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('导出成功'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('成功导出 ${results.length} 个文件：'),
              const SizedBox(height: 8),
              ...results.map((result) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text('• ${result.format.name.toUpperCase()}: ${result.fileName}'),
              )),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
  
  /// 显示错误对话框
  void _showErrorDialog(BuildContext context, String title, String message) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }
}

/// 小说封面设计组件
class NovelCoverDesign extends StatelessWidget {
  const NovelCoverDesign({
    super.key,
    required this.bgColor,
    required this.id,
    this.index,
  });

  final Color bgColor;
  final String id;
  final int? index;

  @override
  Widget build(BuildContext context) {
    final designType = index != null ? index! % 5 : id.hashCode % 5;

    switch (designType) {
      case 0:
        return CirclesDesign(baseColor: bgColor);
      case 1:
        return StripeDesign(baseColor: bgColor);
      case 2:
        return WaveDesign(baseColor: bgColor);
      case 3:
        return GridDesign(baseColor: bgColor);
      default:
        return GeometricDesign(baseColor: bgColor);
    }
  }
}

/// 圆形设计
class CirclesDesign extends StatelessWidget {
  const CirclesDesign({
    super.key,
    required this.baseColor,
  });

  final Color baseColor;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: CirclePainter(
            baseColor: baseColor,
            color: baseColor.withOpacity(0.5),
          ),
          size: const Size.square(200), // 给CustomPaint一个确定的大小
        ),
        Center(
          child: Icon(
            Icons.auto_stories,
            size: 28,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }
}

/// 条纹设计
class StripeDesign extends StatelessWidget {
  const StripeDesign({
    super.key,
    required this.baseColor,
  });

  final Color baseColor;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Positioned.fill(
          child: Opacity(
            opacity: 0.7,
            child: Stack(
              children: [
                Positioned(
                  top: 0,
                  left: 15,
                  bottom: 0,
                  child: Container(
                    width: 4,
                    color: baseColor.withGreen(180).withOpacity(0.8),
                  ),
                ),
                Positioned(
                  top: 0,
                  left: 35,
                  bottom: 20,
                  child: Container(
                    width: 6,
                    color: baseColor.withBlue(180).withOpacity(0.7),
                  ),
                ),
                Positioned(
                  top: 40,
                  right: 10,
                  child: Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: baseColor.withRed(200),
                    ),
                  ),
                ),
                Positioned(
                  bottom: 15,
                  left: 50,
                  child: Container(
                    width: 16,
                    height: 16,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: baseColor.withGreen(200),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        Center(
          child: Icon(
            Icons.menu_book,
            size: 28,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }
}

/// 波浪设计
class WaveDesign extends StatelessWidget {
  const WaveDesign({
    super.key,
    required this.baseColor,
  });

  final Color baseColor;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Opacity(
          opacity: 0.5,
          child: ClipPath(
            clipper: WaveClipper(),
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [baseColor.withRed(200), baseColor.withBlue(200)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
            ),
          ),
        ),
        Center(
          child: Icon(
            Icons.book_outlined,
            size: 28,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }
}

/// 网格设计
class GridDesign extends StatelessWidget {
  const GridDesign({
    super.key,
    required this.baseColor,
  });

  final Color baseColor;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: GridPainter(
            color: baseColor.withOpacity(0.5),
            lineWidth: 1.0,
            spacing: 10.0,
          ),
          size: const Size.square(200), // 给CustomPaint一个确定的大小
        ),
        Center(
          child: Icon(
            Icons.chrome_reader_mode,
            size: 28,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }
}

/// 几何设计
class GeometricDesign extends StatelessWidget {
  const GeometricDesign({
    super.key,
    required this.baseColor,
  });

  final Color baseColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox.expand(
      child: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: Opacity(
              opacity: 0.6,
              child: Transform.rotate(
                angle: -0.5,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    Positioned(
                      top: 10,
                      left: 10,
                      child: Container(
                        width: 50,
                        height: 50,
                        color: baseColor.withBlue(200).withGreen(150),
                      ),
                    ),
                    Positioned(
                      bottom: 20,
                      right: 15,
                      child: Container(
                        width: 80,
                        height: 30,
                        color: baseColor.withRed(220).withGreen(180),
                      ),
                    ),
                    Positioned(
                      top: 40,
                      right: 40,
                      child: Container(
                        width: 20,
                        height: 70,
                        color: baseColor.withGreen(200).withRed(150),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Center(
            child: Icon(
              Icons.edit_document,
              size: 28,
              color: WebTheme.black.withOpacity(0.15),
            ),
          ),
        ],
      ),
    );
  }
}

/// 工具类 - 设计辅助
class NovelCardDesignUtils {
  // 获取随机柔和颜色
  static Color getRandomPastelColor(String id, [int? index, bool isDarkMode = false]) {
    final List<Color> lightColors = [
      const Color(0xFFBBDEFB), // Light Blue
      const Color(0xFFC8E6C9), // Light Green
      const Color(0xFFFFE0B2), // Light Orange
      const Color(0xFFF8BBD0), // Light Pink
      const Color(0xFFE1BEE7), // Light Purple
      const Color(0xFFB2DFDB), // Light Teal
      const Color(0xFFFFF9C4), // Light Yellow
      const Color(0xFFB3E5FC), // Light Cyan
      const Color(0xFFFFCCBC), // Light Deep Orange
      const Color(0xFFC5CAE9), // Light Indigo
    ];

    final List<Color> darkColors = [
      const Color(0xFF1E3A8A), // Dark Blue
      const Color(0xFF166534), // Dark Green
      const Color(0xFF9A3412), // Dark Orange
      const Color(0xFF9D174D), // Dark Pink
      const Color(0xFF7C2D92), // Dark Purple
      const Color(0xFF0F766E), // Dark Teal
      const Color(0xFF92400E), // Dark Yellow
      const Color(0xFF0E7490), // Dark Cyan
      const Color(0xFFEA580C), // Dark Deep Orange
      const Color(0xFF3730A3), // Dark Indigo
    ];

    final colors = isDarkMode ? darkColors : lightColors;

    // 如果提供了索引，使用索引选择颜色，否则使用ID的哈希码
    if (index != null) {
      return colors[index % colors.length];
    }

    return colors[id.hashCode.abs() % colors.length];
  }
}

/// 波浪裁剪器
class WaveClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) {
    var path = Path();
    path.lineTo(0, size.height * 0.8);

    var firstControlPoint = Offset(size.width / 4, size.height);
    var firstEndPoint = Offset(size.width / 2.2, size.height * 0.85);
    path.quadraticBezierTo(
      firstControlPoint.dx,
      firstControlPoint.dy,
      firstEndPoint.dx,
      firstEndPoint.dy,
    );

    var secondControlPoint =
        Offset(size.width - (size.width / 3.5), size.height * 0.65);
    var secondEndPoint = Offset(size.width, size.height * 0.7);
    path.quadraticBezierTo(
      secondControlPoint.dx,
      secondControlPoint.dy,
      secondEndPoint.dx,
      secondEndPoint.dy,
    );

    path.lineTo(size.width, 0);
    path.close();
    return path;
  }

  @override
  bool shouldReclip(CustomClipper<Path> oldClipper) => false;
}

/// 网格绘制器
class GridPainter extends CustomPainter {
  GridPainter({
    required this.color,
    required this.lineWidth,
    required this.spacing,
  });
  final Color color;
  final double lineWidth;
  final double spacing;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = lineWidth
      ..style = PaintingStyle.stroke;

    // 水平线
    for (double y = 0; y <= size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }

    // 垂直线
    for (double x = 0; x <= size.width; x += spacing) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(CustomPainter oldPainter) => false;
}

/// 圆形绘制器
class CirclePainter extends CustomPainter {
  CirclePainter({
    required this.color,
    required this.baseColor,
  });
  final Color color;
  final Color baseColor;

  @override
  void paint(Canvas canvas, Size size) {
    final centerX = size.width / 2;
    final centerY = size.height / 2;

    // 绘制多个同心圆
    for (int i = 5; i > 0; i--) {
      final radius = (size.width / 2) * (i / 5);
      final paint = Paint()
        ..color = i % 2 == 0 ? color : baseColor.withOpacity(0.3)
        ..style = PaintingStyle.fill;

      canvas.drawCircle(Offset(centerX, centerY), radius, paint);
    }
  }

  @override
  bool shouldRepaint(CustomPainter oldPainter) => false;
}
