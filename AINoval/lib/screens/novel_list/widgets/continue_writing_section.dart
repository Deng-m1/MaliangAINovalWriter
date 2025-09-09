import 'package:ainoval/blocs/novel_list/novel_list_bloc.dart';
import 'package:ainoval/models/novel_summary.dart';
import 'package:ainoval/screens/editor/editor_screen.dart';
import 'package:ainoval/utils/date_formatter.dart';
import 'package:ainoval/utils/web_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

/// 继续写作区域组件
class ContinueWritingSection extends StatelessWidget {
  const ContinueWritingSection({
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    // 如果屏幕非常窄，则直接隐藏此区域
    if (screenWidth < 350) {
      return const SizedBox.shrink();
    }

    return BlocBuilder<NovelListBloc, NovelListState>(
      builder: (context, state) {
        if (state is NovelListLoaded && state.novels.isNotEmpty) {
          final recentNovels = List<NovelSummary>.from(state.novels)
            ..sort((a, b) => b.lastEditTime.compareTo(a.lastEditTime));

          if (recentNovels.length > 3) {
            recentNovels.removeRange(3, recentNovels.length);
          }

          return Container(
            color: WebTheme.getSurfaceColor(context),
            padding: const EdgeInsets.only(top: 16, bottom: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SectionHeader(
                  icon: Icons.edit_note,
                  title: '继续写作',
                ),
                const SizedBox(height: 12),
                // 使用LayoutBuilder获取可用空间
                LayoutBuilder(builder: (context, constraints) {
                  // 根据可用宽度动态计算卡片高度和数量
                  double cardHeight;
                  int visibleCards;

                  if (constraints.maxWidth < 450) {
                    cardHeight = 120.0; // 进一步增加高度
                    visibleCards = 1; // 只显示一张卡片
                  } else if (constraints.maxWidth < 600) {
                    cardHeight = 140.0; // 进一步增加高度
                    visibleCards = 2; // 显示两张卡片
                  } else {
                    cardHeight = 160.0; // 进一步增加高度
                    visibleCards = 3; // 显示所有卡片
                  }

                  // 限制显示的卡片数量
                  final displayNovels =
                      recentNovels.take(visibleCards).toList();

                  return SizedBox(
                    height: cardHeight,
                    child: ListView.builder(
                      shrinkWrap: true,
                      scrollDirection: Axis.horizontal,
                      itemCount: displayNovels.length,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemBuilder: (context, index) {
                        final novel = displayNovels[index];

                        // 计算卡片宽度: 窄屏幕下宽度更窄，确保卡片不会过大
                        double cardWidth;
                        if (constraints.maxWidth < 450) {
                          cardWidth =
                              constraints.maxWidth * 0.85; // 非常窄的屏幕使用85%宽度
                        } else if (constraints.maxWidth < 600) {
                          cardWidth = constraints.maxWidth * 0.6; // 窄屏幕使用60%宽度
                        } else {
                          cardWidth = 280.0; // 宽屏幕使用固定宽度
                        }

                        return RecentNovelCard(
                          novel: novel,
                          index: index,
                          cardWidth: cardWidth,
                        );
                      },
                    ),
                  );
                }),
              ],
            ),
          );
        }

        return const SizedBox.shrink();
      },
    );
  }
}

/// 最近编辑过的小说卡片
class RecentNovelCard extends StatelessWidget {
  const RecentNovelCard({
    super.key,
    required this.novel,
    required this.index,
    this.cardWidth = 280.0,
  });

  final NovelSummary novel;
  final int index;
  final double cardWidth;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bgColor = _getRandomPastelColor(context, novel.id, index);
    final bool isNarrow = cardWidth < 250;

    return Container(
      width: cardWidth,
      margin: const EdgeInsets.only(left: 4, right: 12),
      child: Card(
        elevation: 3,
        shadowColor: (WebTheme.isDarkMode(context) ? WebTheme.black : WebTheme.grey400).withOpacity(0.15),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: () => _navigateToEditor(context),
          splashColor: WebTheme.getTextColor(context).withOpacity(0.1),
          highlightColor: Colors.transparent,
          child: Row(
            children: [
              // 封面区域 - 宽度等比例缩放
              SizedBox(
                width: isNarrow
                    ? cardWidth * 0.28
                    : cardWidth * 0.33, // 很窄的卡片封面占比更小
                child: RecentNovelCover(
                    novel: novel, bgColor: bgColor, index: index),
              ),

              // 信息区域
              Expanded(
                child: RecentNovelInfo(
                  novel: novel,
                  isCompact: isNarrow, // 窄卡片使用紧凑布局
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 导航到编辑器
  void _navigateToEditor(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => EditorScreen(novel: novel),
      ),
    ).then((_) {
      // 导航返回时刷新小说列表
      context.read<NovelListBloc>().add(LoadNovels());
    });
  }

  // 获取动态的柔和颜色
  Color _getRandomPastelColor(BuildContext context, String id, int index) {
    final theme = Theme.of(context);
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

    final colors = theme.brightness == Brightness.dark ? darkColors : lightColors;
    return colors[index % colors.length];
  }
}

/// 区域标题头组件
class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.icon,
    required this.title,
  });

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final bool isNarrow = screenWidth < 450;
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: isNarrow ? 16 : 24),
      child: Row(
        children: [
          Container(
            padding: EdgeInsets.all(isNarrow ? 6 : 8),
            decoration: BoxDecoration(
              color: Colors.transparent,
              borderRadius: BorderRadius.circular(6),
              border: Border.all(
                color: theme.colorScheme.outlineVariant,
                width: 1.0,
              ),
            ),
            child: Icon(
              icon,
              color: theme.colorScheme.onSurfaceVariant,
              size: isNarrow ? 16 : 18,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            title,
            style: TextStyle(
              fontSize: isNarrow ? 16 : 18,
              fontWeight: FontWeight.bold,
              color: WebTheme.getTextColor(context),
            ),
          ),
        ],
      ),
    );
  }
}

/// 最近小说封面组件
class RecentNovelCover extends StatelessWidget {
  const RecentNovelCover({
    super.key,
    required this.novel,
    required this.bgColor,
    required this.index,
  });

  final NovelSummary novel;
  final Color bgColor;
  final int index;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: double.infinity,
      decoration: BoxDecoration(
        color: bgColor,
        gradient: LinearGradient(
          colors: [
            bgColor,
            bgColor.withOpacity(0.7),
          ],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          // 优先显示封面图片（如果有）
          if (novel.coverUrl.isNotEmpty)
            Image.network(
              novel.coverUrl,
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) {
                // 加载失败时使用生成的设计
                return _buildCoverDesign(bgColor, novel.id, index);
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
                    color: WebTheme.getTextColor(context),
                  ),
                );
              },
            )
          else
            // 使用生成的设计作为默认封面
            _buildCoverDesign(bgColor, novel.id, index),

          // 进度条
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: LinearProgressIndicator(
              value: novel.completionPercentage,
              backgroundColor: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey100,
              color: WebTheme.getTextColor(context),
              minHeight: 2,
            ),
          ),
        ],
      ),
    );
  }

  // 构建封面设计
  Widget _buildCoverDesign(Color baseColor, String id, int index) {
    final designType = index % 5;

    switch (designType) {
      case 0:
        return _buildCircleDesign(baseColor);
      case 1:
        return _buildStripeDesign(baseColor);
      case 2:
        return _buildWaveDesign(baseColor);
      case 3:
        return _buildGridDesign(baseColor);
      default:
        return _buildGeometricDesign(baseColor);
    }
  }

  // 圆形设计
  Widget _buildCircleDesign(Color baseColor) {
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: _CirclePainter(
            baseColor: baseColor,
            color: baseColor.withOpacity(0.5),
          ),
          size: const Size.square(200),
        ),
        Center(
          child: Icon(
            Icons.auto_stories,
            size: 24,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }

  // 条纹设计
  Widget _buildStripeDesign(Color baseColor) {
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
                    width: 3,
                    color: baseColor.withGreen(180).withOpacity(0.8),
                  ),
                ),
                Positioned(
                  top: 0,
                  left: 28,
                  bottom: 20,
                  child: Container(
                    width: 4,
                    color: baseColor.withBlue(180).withOpacity(0.7),
                  ),
                ),
                Positioned(
                  top: 40,
                  right: 10,
                  child: Container(
                    width: 20,
                    height: 20,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: baseColor.withRed(200),
                    ),
                  ),
                ),
                Positioned(
                  bottom: 15,
                  left: 40,
                  child: Container(
                    width: 12,
                    height: 12,
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
            size: 24,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }

  // 波浪设计
  Widget _buildWaveDesign(Color baseColor) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Opacity(
          opacity: 0.5,
          child: ClipPath(
            clipper: _WaveClipper(),
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
            size: 24,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }

  // 网格设计
  Widget _buildGridDesign(Color baseColor) {
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: _GridPainter(
            color: baseColor.withOpacity(0.5),
            lineWidth: 0.8,
            spacing: 8.0,
          ),
          size: const Size.square(200),
        ),
        Center(
          child: Icon(
            Icons.chrome_reader_mode,
            size: 24,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }

  // 几何设计
  Widget _buildGeometricDesign(Color baseColor) {
    return Stack(
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
                      width: 40,
                      height: 40,
                      color: baseColor.withBlue(200).withGreen(150),
                    ),
                  ),
                  Positioned(
                    bottom: 15,
                    right: 15,
                    child: Container(
                      width: 60,
                      height: 25,
                      color: baseColor.withRed(220).withGreen(180),
                    ),
                  ),
                  Positioned(
                    top: 35,
                    right: 30,
                    child: Container(
                      width: 15,
                      height: 50,
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
            size: 24,
            color: WebTheme.black.withOpacity(0.15),
          ),
        ),
      ],
    );
  }
}

/// 最近小说信息组件
class RecentNovelInfo extends StatelessWidget {
  const RecentNovelInfo({
    super.key,
    required this.novel,
    this.isCompact = false,
  });

  final NovelSummary novel;
  final bool isCompact;

  @override
  Widget build(BuildContext context) {
    // 使用 LayoutBuilder 来获取可用空间
    return LayoutBuilder(
      builder: (context, constraints) {
        // 根据可用高度决定显示哪些信息
        final availableHeight = constraints.maxHeight;
        
        if (isCompact) {
          // 超紧凑模式 - 只显示最重要的信息
          return Padding(
            padding: const EdgeInsets.all(6),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                // 标题始终显示
                Text(
                  novel.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                    color: WebTheme.getTextColor(context),
                  ),
                ),
                const SizedBox(height: 2),
                
                // 时间或系列名（二选一）
                if (novel.seriesName.isNotEmpty)
                  _buildSeriesInfo(context)
                else
                  _buildTimeInfo(context),
                
                // 进度条始终显示
                const SizedBox(height: 3),
                ClipRRect(
                  borderRadius: BorderRadius.circular(2),
                  child: LinearProgressIndicator(
                    value: novel.completionPercentage,
                    backgroundColor: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey100,
                    color: WebTheme.getTextColor(context),
                    minHeight: 2,
                  ),
                ),
                
                // 如果空间足够，显示字数
                if (availableHeight > 70) ...[
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      Icon(
                        Icons.text_fields,
                        size: 10,
                        color: WebTheme.getSecondaryTextColor(context),
                      ),
                      const SizedBox(width: 3),
                      Text(
                        '${_formatNumber(novel.wordCount)}字',
                        style: TextStyle(
                          fontSize: 9,
                          color: WebTheme.getSecondaryTextColor(context),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          );
        } else {
          // 标准模式 - 使用 Flexible 控制子组件大小
          return Padding(
            padding: const EdgeInsets.all(10), // 稍微减小内边距
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 标题
                Text(
                  novel.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: WebTheme.getTextColor(context),
                  ),
                ),
                const SizedBox(height: 4),
                
                // 时间信息
                _buildTimeInfo(context),
                
                // 使用 Flexible 包装可能溢出的内容
                Flexible(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const SizedBox(height: 3),
                      
                      // 字数和系列信息
                      Row(
                        children: [
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
                                  '${_formatNumber(novel.wordCount)}字',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w500,
                                    color: WebTheme.getTextColor(context),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          if (novel.seriesName.isNotEmpty && availableHeight > 100) ...[
                            const SizedBox(width: 8),
                            Flexible(
                              child: Text(
                                novel.seriesName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 11,
                                  fontStyle: FontStyle.italic,
                                  color: WebTheme.getSecondaryTextColor(context),
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      
                      // 结构信息（如果空间足够）
                      if (availableHeight > 90) ...[
                        const SizedBox(height: 3),
                        _buildStructureInfo(context),
                      ],
                    ],
                  ),
                ),
                
                // 进度条
                const SizedBox(height: 4),
                ClipRRect(
                  borderRadius: BorderRadius.circular(2),
                  child: LinearProgressIndicator(
                    value: novel.completionPercentage,
                    backgroundColor: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey100,
                    color: WebTheme.getTextColor(context),
                    minHeight: 3,
                  ),
                ),
              ],
            ),
          );
        }
      },
    );
  }

  // 构建系列信息组件
  Widget _buildSeriesInfo(BuildContext context) {
    return Row(
      children: [
        Icon(
          Icons.bookmark_border,
          size: 10,
          color: WebTheme.getSecondaryTextColor(context),
        ),
        const SizedBox(width: 3),
        Expanded(
          child: Text(
            novel.seriesName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 9,
              color: WebTheme.getSecondaryTextColor(context),
            ),
          ),
        ),
      ],
    );
  }

  // 构建时间信息组件
  Widget _buildTimeInfo(BuildContext context) {
    return Row(
      children: [
        Icon(
          Icons.access_time,
          size: isCompact ? 10 : 12,
          color: WebTheme.getSecondaryTextColor(context),
        ),
        const SizedBox(width: 3),
        Expanded(
          child: Text(
            isCompact
                ? DateFormatter.formatRelative(novel.lastEditTime)
                : '上次: ${DateFormatter.formatRelative(novel.lastEditTime)}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: isCompact ? 9 : 11,
              color: WebTheme.getSecondaryTextColor(context),
            ),
          ),
        ),
      ],
    );
  }

  // 构建字数信息组件
  // removed unused _buildWordCountInfo to satisfy lints

  // 构建卷、章节、场景数量信息组件
  Widget _buildStructureInfo(BuildContext context) {
    return Row(
      children: [
        Icon(
          Icons.library_books_outlined,
          size: isCompact ? 9 : 10,
          color: WebTheme.getSecondaryTextColor(context),
        ),
        const SizedBox(width: 3),
        Expanded(
          child: Text(
            '${novel.actCount}卷 / ${novel.chapterCount}章 / ${novel.sceneCount}场景',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: isCompact ? 8 : 9,
              color: WebTheme.getSecondaryTextColor(context),
            ),
          ),
        ),
      ],
    );
  }

  // 格式化数字显示
  String _formatNumber(int number) {
    if (number < 1000) {
      return number.toString();
    } else if (number < 10000) {
      return '${(number / 1000).toStringAsFixed(1)}K';
    } else {
      return '${(number / 10000).toStringAsFixed(1)}万';
    }
  }
}

// 波浪裁剪器
class _WaveClipper extends CustomClipper<Path> {
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

// 网格绘制器
class _GridPainter extends CustomPainter {
  _GridPainter({
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

// 圆形绘制器
class _CirclePainter extends CustomPainter {
  _CirclePainter({
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
