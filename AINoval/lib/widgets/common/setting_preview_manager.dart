import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';
import 'package:ainoval/blocs/setting/setting_bloc.dart';
import 'package:ainoval/models/novel_setting_item.dart';
import 'package:ainoval/models/setting_type.dart';
import 'package:ainoval/models/setting_group.dart';
import 'package:ainoval/utils/web_theme.dart';
import 'package:ainoval/utils/logger.dart';
import 'package:ainoval/screens/editor/widgets/novel_setting_detail.dart';
import 'package:ainoval/services/api_service/repositories/novel_setting_repository.dart';
import 'package:ainoval/services/api_service/repositories/storage_repository.dart';
import 'package:ainoval/screens/editor/managers/editor_layout_manager.dart';
import 'package:ainoval/widgets/common/universal_card.dart';

/// 通用设定预览卡片管理器
/// 
/// 提供统一的设定预览卡片显示和管理功能，应用全局样式和主题
/// 支持点击标题打开详情编辑卡片，确保Provider正确传递
class SettingPreviewManager {
  static OverlayEntry? _overlayEntry;
  static bool _isShowing = false;

  /// 显示设定预览卡片
  /// 
  /// [context] 上下文，必须包含SettingBloc、NovelSettingRepository、StorageRepository
  /// [settingId] 设定条目ID
  /// [novelId] 小说ID
  /// [position] 显示位置
  /// [onClose] 关闭回调
  /// [onDetailOpened] 详情卡片打开回调
  static void show({
    required BuildContext context,
    required String settingId,
    required String novelId,
    required Offset position,
    VoidCallback? onClose,
    VoidCallback? onDetailOpened,
  }) {
    if (_isShowing) {
      hide();
    }

    try {
      // 🚀 预检查必要的Provider实例
      final settingBloc = context.read<SettingBloc>();
      final settingRepository = context.read<NovelSettingRepository>();
      final storageRepository = context.read<StorageRepository>();
      final editorLayoutManager = context.read<EditorLayoutManager>();
      
      // 🎯 查找滚动上下文
      final scrollableState = Scrollable.maybeOf(context);
      AppLogger.d('SettingPreviewManager', '🔍 查找滚动上下文: ${scrollableState != null ? "找到" : "未找到"}');

      AppLogger.i('SettingPreviewManager', '📍 显示设定预览卡片: $settingId');

      _overlayEntry = OverlayEntry(
        builder: (overlayContext) => Stack(
          children: [
            // 🎯 智能背景遮罩 - 只在点击编辑区域时关闭
            Positioned.fill(
              child: GestureDetector(
                onTap: () {
                  AppLogger.d('SettingPreviewManager', '🎯 点击编辑区域，关闭预览卡片');
                  hide();
                  onClose?.call();
                },
                child: Container(
                  color: Colors.transparent,
                ),
              ),
            ),

            // 设定预览卡片 - 通过MultiProvider确保所有依赖都可用
            MultiProvider(
              providers: [
                BlocProvider<SettingBloc>.value(value: settingBloc),
                Provider<NovelSettingRepository>.value(value: settingRepository),
                Provider<StorageRepository>.value(value: storageRepository),
                ChangeNotifierProvider<EditorLayoutManager>.value(value: editorLayoutManager),
              ],
              child: _UniversalSettingPreviewCard(
                settingId: settingId,
                novelId: novelId,
                position: position,
                scrollPosition: scrollableState?.position,
                onClose: () {
                  hide();
                  onClose?.call();
                },
                onDetailOpened: onDetailOpened,
              ),
            ),
          ],
        ),
      );

      Overlay.of(context).insert(_overlayEntry!);
      _isShowing = true;

      AppLogger.i('SettingPreviewManager', '✅ 设定预览卡片已显示');
    } catch (e) {
      AppLogger.e('SettingPreviewManager', '显示设定预览卡片失败', e);
    }
  }

  /// 隐藏设定预览卡片
  static void hide() {
    if (_overlayEntry != null) {
      _overlayEntry!.remove();
      _overlayEntry = null;
      _isShowing = false;
    }
  }

  /// 检查是否正在显示
  static bool get isShowing => _isShowing;
}

/// 通用设定预览卡片组件
/// 
/// 采用全局样式和主题，提供一致的用户体验
class _UniversalSettingPreviewCard extends StatefulWidget {
  final String settingId;
  final String novelId;
  final Offset position;
  final ScrollPosition? scrollPosition;
  final VoidCallback? onClose;
  final VoidCallback? onDetailOpened;

  const _UniversalSettingPreviewCard({
    Key? key,
    required this.settingId,
    required this.novelId,
    required this.position,
    this.scrollPosition,
    this.onClose,
    this.onDetailOpened,
  }) : super(key: key);

  @override
  State<_UniversalSettingPreviewCard> createState() => _UniversalSettingPreviewCardState();
}

class _UniversalSettingPreviewCardState extends State<_UniversalSettingPreviewCard>
    with TickerProviderStateMixin {
  static const String _tag = 'UniversalSettingPreviewCard';

  late AnimationController _animationController;
  late Animation<double> _scaleAnimation;
  late Animation<double> _opacityAnimation;
  late AnimationController _positionController;
  late Animation<Offset> _positionAnimation;

  NovelSettingItem? _settingItem;
  SettingGroup? _settingGroup;
  bool _isLoading = true;
  
  // 🎯 智能浮动定位相关状态
  Offset _currentPosition = Offset.zero;
  double _lastScrollOffset = 0;
  ScrollPosition? _scrollPosition;
  bool _isFollowingScroll = true;

  @override
  void initState() {
    super.initState();

    // 初始化位置
    _currentPosition = widget.position;

    _animationController = AnimationController(
      duration: const Duration(milliseconds: 250),
      vsync: this,
    );
    
    // 🎯 智能定位动画控制器
    _positionController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );

    _scaleAnimation = Tween<double>(
      begin: 0.85,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeOutCubic,
    ));

    _opacityAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeOut,
    ));
    
    _positionAnimation = Tween<Offset>(
      begin: _currentPosition,
      end: _currentPosition,
    ).animate(CurvedAnimation(
      parent: _positionController,
      curve: Curves.easeOutCubic,
    ));

    _loadSettingData();
    _animationController.forward();
    
    // 🎯 延迟初始化滚动监听，等待Widget完全构建
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initializeScrollListener();
    });
  }

  @override
  void dispose() {
    _animationController.dispose();
    _positionController.dispose();
    _scrollPosition?.removeListener(_onScrollChanged);
    super.dispose();
  }
  
  /// 🎯 初始化滚动监听器
  void _initializeScrollListener() {
    try {
      AppLogger.d(_tag, '🔍 开始初始化滚动监听器...');
      
      // 方式1: 使用传入的ScrollPosition
      if (widget.scrollPosition != null) {
        _scrollPosition = widget.scrollPosition!;
        _lastScrollOffset = _scrollPosition!.pixels;
        _scrollPosition!.addListener(_onScrollChanged);
        AppLogger.i(_tag, '✅ 滚动监听器初始化成功 - 方式1: 传入的ScrollPosition');
        AppLogger.d(_tag, '📍 初始滚动位置: ${_lastScrollOffset}');
        return;
      }
      
      // 方式2: 查找最近的ScrollableState
      final scrollableState = Scrollable.maybeOf(context);
      if (scrollableState != null) {
        _scrollPosition = scrollableState.position;
        _lastScrollOffset = _scrollPosition!.pixels;
        _scrollPosition!.addListener(_onScrollChanged);
        AppLogger.i(_tag, '✅ 滚动监听器初始化成功 - 方式2: Scrollable.maybeOf');
        AppLogger.d(_tag, '📍 初始滚动位置: ${_lastScrollOffset}');
        return;
      }
      
             // 方式2: 向上搜索父级Widget树寻找滚动区域
       BuildContext? searchContext = context;
       int searchDepth = 0;
       const maxSearchDepth = 5;
       
       searchContext.visitAncestorElements((ancestor) {
         if (searchDepth >= maxSearchDepth) return false;
         
         final scrollableState = Scrollable.maybeOf(ancestor);
         if (scrollableState != null) {
           _scrollPosition = scrollableState.position;
           _lastScrollOffset = _scrollPosition!.pixels;
           _scrollPosition!.addListener(_onScrollChanged);
           AppLogger.i(_tag, '✅ 滚动监听器初始化成功 - 方式2: 向上搜索深度$searchDepth');
           AppLogger.d(_tag, '📍 初始滚动位置: ${_lastScrollOffset}');
           return false; // 找到后停止搜索
         }
         
         searchDepth++;
         return true; // 继续向上搜索
       });
       
       // 如果已经找到滚动位置，直接返回
       if (_scrollPosition != null) return;
      
      // 方式3: 延迟重试，等待Overlay完全加载
      AppLogger.w(_tag, '⚠️ 首次未找到滚动上下文，1秒后重试...');
      Future.delayed(const Duration(seconds: 1), () {
        if (mounted) {
          _retryInitializeScrollListener();
        }
      });
      
    } catch (e) {
      AppLogger.e(_tag, '初始化滚动监听器失败', e);
      // 延迟重试
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted) {
          _retryInitializeScrollListener();
        }
      });
    }
  }
  
  /// 🎯 重试初始化滚动监听器
  void _retryInitializeScrollListener() {
    try {
      AppLogger.d(_tag, '🔄 重试初始化滚动监听器...');
      
      final scrollableState = Scrollable.maybeOf(context);
      if (scrollableState != null) {
        _scrollPosition = scrollableState.position;
        _lastScrollOffset = _scrollPosition!.pixels;
        _scrollPosition!.addListener(_onScrollChanged);
        AppLogger.i(_tag, '✅ 滚动监听器重试初始化成功');
        AppLogger.d(_tag, '📍 初始滚动位置: ${_lastScrollOffset}');
      } else {
        AppLogger.e(_tag, '❌ 重试后仍未找到可滚动的上下文');
      }
    } catch (e) {
      AppLogger.e(_tag, '重试初始化滚动监听器失败', e);
    }
  }
  
  /// 🎯 处理滚动变化 - 智能调整卡片位置
  void _onScrollChanged() {
    if (!_isFollowingScroll || _scrollPosition == null || !mounted) return;
    
    final currentScrollOffset = _scrollPosition!.pixels;
    final scrollDelta = currentScrollOffset - _lastScrollOffset;
    _lastScrollOffset = currentScrollOffset;
    
    // 🔍 调试信息：记录滚动变化
    AppLogger.d(_tag, '🔄 滚动事件: 当前位置=${currentScrollOffset.toStringAsFixed(1)}, 变化=${scrollDelta.toStringAsFixed(1)}');
    
    // 忽略极小的滚动变化，避免过度敏感
    if (scrollDelta.abs() < 0.5) return;
    
    // 计算新位置
    final screenSize = MediaQuery.of(context).size;
    const cardHeight = 220.0;
    const cardWidth = 340.0;
    const topMargin = 16.0;
    const bottomMargin = 16.0;
    
    double newTop = _currentPosition.dy - scrollDelta;
    double newLeft = _currentPosition.dx;
    
    // 🎯 智能边界处理 - 当向下滚动时卡片逐渐向顶部靠拢
    if (scrollDelta > 0) { // 向下滚动
      // 如果卡片即将滚出上边界，让它停留在顶部
      if (newTop < topMargin) {
        newTop = topMargin;
      }
    } else if (scrollDelta < 0) { // 向上滚动
      // 如果卡片即将滚出下边界，让它停留在底部
      if (newTop + cardHeight > screenSize.height - bottomMargin) {
        newTop = screenSize.height - cardHeight - bottomMargin;
      }
    }
    
    // 水平位置边界检查
    if (newLeft + cardWidth > screenSize.width - 16) {
      newLeft = screenSize.width - cardWidth - 16;
    }
    if (newLeft < 16) {
      newLeft = 16;
    }
    
    final newPosition = Offset(newLeft, newTop);
    
    // 只有位置真正改变时才更新
    if (newPosition != _currentPosition) {
      _updatePosition(newPosition);
    }
  }
  
  /// 🎯 平滑更新卡片位置
  void _updatePosition(Offset newPosition) {
    if (!mounted) return;
    
    AppLogger.d(_tag, '📍 更新卡片位置: ${_currentPosition.dx.toStringAsFixed(1)},${_currentPosition.dy.toStringAsFixed(1)} → ${newPosition.dx.toStringAsFixed(1)},${newPosition.dy.toStringAsFixed(1)}');
    
    _positionAnimation = Tween<Offset>(
      begin: _currentPosition,
      end: newPosition,
    ).animate(CurvedAnimation(
      parent: _positionController,
      curve: Curves.easeOutCubic,
    ));
    
    _currentPosition = newPosition;
    _positionController.forward(from: 0);
  }

  /// 加载设定数据
  void _loadSettingData() {
    try {
      final settingBloc = context.read<SettingBloc>();
      final state = settingBloc.state;

      AppLogger.d(_tag, '加载设定数据: ${widget.settingId}');

      // 查找设定条目
      _settingItem = state.items.where((item) => item.id == widget.settingId).firstOrNull;

      if (_settingItem != null) {
        // 查找设定组
        _settingGroup = state.groups.where(
          (group) => group.itemIds?.contains(widget.settingId) == true,
        ).firstOrNull;

        AppLogger.d(_tag, '找到设定: ${_settingItem!.name}, 组: ${_settingGroup?.name ?? "无"}');
      } else {
        AppLogger.w(_tag, '未找到设定: ${widget.settingId}');
      }

      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      AppLogger.e(_tag, '加载设定数据失败', e);
      setState(() {
        _isLoading = false;
      });
    }
  }

  /// 获取设定类型图标
  IconData _getTypeIcon() {
    if (_settingItem?.type == null) return Icons.article;

    final settingType = SettingType.fromValue(_settingItem!.type!);
    switch (settingType) {
      case SettingType.character:
        return Icons.person;
      case SettingType.location:
        return Icons.place;
      case SettingType.item:
        return Icons.inventory_2;
      case SettingType.lore:
        return Icons.public;
      case SettingType.event:
        return Icons.event;
      case SettingType.concept:
        return Icons.auto_awesome;
      case SettingType.faction:
        return Icons.groups;
      case SettingType.creature:
        return Icons.pets;
      case SettingType.magicSystem:
        return Icons.auto_fix_high;
      case SettingType.technology:
        return Icons.science;
      case SettingType.culture:
        return Icons.emoji_people;
      case SettingType.history:
        return Icons.history;
      case SettingType.organization:
        return Icons.apartment;
      case SettingType.worldview:
        return Icons.public;
      case SettingType.pleasurePoint:
        return Icons.whatshot;
      case SettingType.anticipationHook:
        return Icons.bolt;
      case SettingType.theme:
        return Icons.category;
      case SettingType.tone:
        return Icons.tonality;
      case SettingType.style:
        return Icons.brush;
      case SettingType.trope:
        return Icons.theater_comedy;
      case SettingType.plotDevice:
        return Icons.schema;
      case SettingType.powerSystem:
        return Icons.flash_on;
      case SettingType.timeline:
        return Icons.timeline;
      case SettingType.religion:
        return Icons.account_balance;
      case SettingType.politics:
        return Icons.gavel;
      case SettingType.economy:
        return Icons.attach_money;
      case SettingType.geography:
        return Icons.map;
      default:
        return Icons.article;
    }
  }

  /// 获取设定类型显示名称
  String _getTypeDisplayName() {
    if (_settingItem?.type == null) return '其他';
    return SettingType.fromValue(_settingItem!.type!).displayName;
  }

  /// 处理标题点击 - 修复Provider传递问题
  void _handleTitleTap() {
    AppLogger.d(_tag, '点击设定标题，打开详情卡片: ${_settingItem?.name}');

    if (_settingItem == null) return;

    // 关闭当前预览卡片
    _close();

    // 延迟打开详情卡片，确保预览卡片完全关闭并且context仍然有效
    Future.delayed(const Duration(milliseconds: 150), () {
      // 🚀 修复：使用根context而不是当前组件的context，避免Provider丢失
      final rootContext = context;
      if (!rootContext.mounted) {
        AppLogger.w(_tag, '上下文已失效，无法打开详情卡片');
        return;
      }

      try {
        // 🚀 在打开详情卡片前再次验证Provider可用性
        rootContext.read<SettingBloc>();
        rootContext.read<NovelSettingRepository>();
        rootContext.read<StorageRepository>();

        AppLogger.d(_tag, '✅ Provider验证通过，打开详情卡片');

        FloatingNovelSettingDetail.show(
          context: rootContext,
          itemId: _settingItem!.id,
          novelId: widget.novelId,
          groupId: _settingGroup?.id,
          isEditing: false,
          onSave: (item, groupId) {
            AppLogger.i(_tag, '设定详情保存成功: ${item.name}');
          },
          onCancel: () {
            AppLogger.d(_tag, '设定详情编辑取消');
          },
        );

        widget.onDetailOpened?.call();
      } catch (e) {
        AppLogger.e(_tag, '打开详情卡片时Provider验证失败', e);
        // 尝试显示错误提示
        if (rootContext.mounted) {
          ScaffoldMessenger.of(rootContext).showSnackBar(
            const SnackBar(
              content: Text('无法打开设定详情，请重试'),
              backgroundColor: Colors.red,
              duration: Duration(seconds: 2),
            ),
          );
        }
      }
    });
  }

  /// 关闭卡片
  void _close() {
    _animationController.reverse().then((_) {
      widget.onClose?.call();
    });
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;

    // 🎨 使用通用卡片组件 - 应用全局样式和主题
    const cardWidth = 340.0;
    const cardHeight = 220.0;

    return AnimatedBuilder(
      animation: Listenable.merge([_animationController, _positionController]),
      builder: (context, child) {
        // 🎯 使用动态位置或静态位置
        final position = _positionController.isAnimating 
            ? _positionAnimation.value 
            : _currentPosition;
            
        // 智能位置计算，确保卡片不超出屏幕边界
        double left = position.dx;
        double top = position.dy;

        // 调整水平位置
        if (left + cardWidth > screenSize.width) {
          left = screenSize.width - cardWidth - 16;
        }
        if (left < 16) {
          left = 16;
        }

        // 调整垂直位置
        if (top + cardHeight > screenSize.height) {
          top = position.dy - cardHeight - 10; // 显示在鼠标上方
        }
        if (top < 16) {
          top = 16;
        }

        return Positioned(
          left: left,
          top: top,
          child: Transform.scale(
            scale: _scaleAnimation.value,
            child: Opacity(
              opacity: _opacityAnimation.value,
              child: GestureDetector(
                // 🎯 点击卡片区域不关闭卡片
                onTap: () {
                  // 阻止事件冒泡
                },
                child: UniversalCard(
                  config: UniversalCardConfig.preview.copyWith(
                    width: cardWidth,
                    showCloseButton: true,
                    showHeader: false, // 我们自定义标题区域
                    padding: EdgeInsets.zero, // 使用自定义padding
                  ),
                  onClose: _close,
                  child: Container(
                    constraints: const BoxConstraints(
                      maxHeight: cardHeight,
                    ),
                    child: _buildCardContent(),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  /// 构建卡片内容
  Widget _buildCardContent() {
    if (_isLoading) {
      return Container(
        padding: const EdgeInsets.all(24),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: WebTheme.getTextColor(context),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                '加载中...',
                style: WebTheme.getAlignedTextStyle(
                  baseStyle: TextStyle(
                    fontSize: 13,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_settingItem == null) {
      return Container(
        padding: const EdgeInsets.all(24),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline,
                size: 32,
                color: WebTheme.getSecondaryTextColor(context),
              ),
              const SizedBox(height: 12),
              Text(
                '设定不存在',
                style: WebTheme.getAlignedTextStyle(
                  baseStyle: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        // 头部区域
        _buildHeader(),

        // 分隔线
        Container(
          height: 1,
          color: WebTheme.grey200,
        ),

        // 内容区域
        Flexible(
          child: _buildContent(),
        ),
      ],
    );
  }

  /// 构建头部区域
  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          // 设定图片或类型图标
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: WebTheme.grey100,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: WebTheme.grey300,
                width: 1,
              ),
            ),
            child: _settingItem!.imageUrl != null && _settingItem!.imageUrl!.isNotEmpty
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(7),
                    child: Image.network(
                      _settingItem!.imageUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stackTrace) {
                        return Icon(
                          _getTypeIcon(),
                          size: 26,
                          color: WebTheme.getTextColor(context),
                        );
                      },
                    ),
                  )
                : Icon(
                    _getTypeIcon(),
                    size: 26,
                    color: WebTheme.getTextColor(context),
                  ),
          ),

          const SizedBox(width: 16),

          // 设定信息
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 设定名称（可点击）
                GestureDetector(
                  onTap: _handleTitleTap,
                  child: MouseRegion(
                    cursor: SystemMouseCursors.click,
                    child: Text(
                      _settingItem!.name,
                      style: WebTheme.getAlignedTextStyle(
                        baseStyle: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w600,
                          color: WebTheme.getTextColor(context),
                          decoration: TextDecoration.underline,
                          decorationColor: WebTheme.getTextColor(context).withOpacity(0.4),
                          decorationThickness: 1.2,
                        ),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),

                const SizedBox(height: 6),

                // 类型和设定组
                Row(
                  children: [
                    // 设定类型
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: WebTheme.getTextColor(context).withOpacity(0.08),
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: Text(
                        _getTypeDisplayName(),
                        style: WebTheme.getAlignedTextStyle(
                          baseStyle: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: WebTheme.getTextColor(context),
                          ),
                        ),
                      ),
                    ),

                    if (_settingGroup != null) ...[
                      const SizedBox(width: 10),
                      // 设定组
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: WebTheme.getSecondaryTextColor(context).withOpacity(0.08),
                          borderRadius: BorderRadius.circular(5),
                        ),
                        child: Text(
                          _settingGroup!.name,
                          style: WebTheme.getAlignedTextStyle(
                            baseStyle: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w500,
                              color: WebTheme.getSecondaryTextColor(context),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),

          // 关闭按钮
          GestureDetector(
            onTap: _close,
            child: MouseRegion(
              cursor: SystemMouseCursors.click,
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: Colors.transparent,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Icon(
                  Icons.close,
                  size: 18,
                  color: WebTheme.getSecondaryTextColor(context),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建内容区域
  Widget _buildContent() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // 描述内容
          if (_settingItem!.description != null && _settingItem!.description!.isNotEmpty) ...[
            Text(
              '描述',
              style: WebTheme.getAlignedTextStyle(
                baseStyle: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: WebTheme.getTextColor(context),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Flexible(
              child: Text(
                _settingItem!.description!,
                style: WebTheme.getAlignedTextStyle(
                  baseStyle: TextStyle(
                    fontSize: 13,
                    height: 1.5,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ] else if (_settingItem!.content != null && _settingItem!.content!.isNotEmpty) ...[
            Text(
              '内容',
              style: WebTheme.getAlignedTextStyle(
                baseStyle: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: WebTheme.getTextColor(context),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Flexible(
              child: Text(
                _settingItem!.content!,
                style: WebTheme.getAlignedTextStyle(
                  baseStyle: TextStyle(
                    fontSize: 13,
                    height: 1.5,
                    color: WebTheme.getSecondaryTextColor(context),
                  ),
                ),
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ] else ...[
            Center(
              child: Text(
                '暂无描述',
                style: WebTheme.getAlignedTextStyle(
                  baseStyle: TextStyle(
                    fontSize: 13,
                    color: WebTheme.getSecondaryTextColor(context).withOpacity(0.6),
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
            ),
          ],

          const SizedBox(height: 12),

          // 提示文本
          Center(
            child: Text(
              '点击标题查看详情',
              style: WebTheme.getAlignedTextStyle(
                baseStyle: TextStyle(
                  fontSize: 11,
                  color: WebTheme.getSecondaryTextColor(context).withOpacity(0.7),
                  fontStyle: FontStyle.italic,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
} 