import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:ainoval/blocs/setting/setting_bloc.dart';
import 'package:ainoval/models/novel_setting_item.dart';
import 'package:ainoval/models/setting_group.dart';
import 'package:ainoval/models/setting_type.dart'; // 导入设定类型枚举
import 'package:ainoval/utils/logger.dart';
import 'package:ainoval/screens/editor/widgets/novel_setting_detail.dart';
import 'package:ainoval/screens/editor/widgets/floating_setting_dialogs.dart';
// import 'package:ainoval/screens/editor/widgets/menu_builder.dart';
// import 'package:ainoval/screens/editor/widgets/dropdown_manager.dart';
import 'package:ainoval/screens/editor/widgets/custom_dropdown.dart';
import 'package:ainoval/widgets/common/app_search_field.dart'; // 导入统一搜索组件
import 'package:ainoval/utils/web_theme.dart'; // 导入全局主题
// import 'dart:async';

/// 小说设定侧边栏组件
/// 
/// 用于管理小说设定条目和设定组，以树状列表方式展示
class NovelSettingSidebar extends StatefulWidget {
  final String novelId;
  
  const NovelSettingSidebar({
    Key? key,
    required this.novelId,
  }) : super(key: key);

  @override
  State<NovelSettingSidebar> createState() => _NovelSettingSidebarState();
}

class _NovelSettingSidebarState extends State<NovelSettingSidebar> 
    with AutomaticKeepAliveClientMixin<NovelSettingSidebar> {
  final TextEditingController _searchController = TextEditingController();
  
  // 展开的设定组ID集合
  final Set<String> _expandedGroupIds = {};
  
  // 分组模式：'type' = 按设定分类分组，'group' = 按设定组分组
  String _groupingMode = 'type'; // 默认使用设定分类分组
  
  // 展开的设定类型集合（用于按类型分组时）
  final Set<String> _expandedTypeIds = {};

  @override
  bool get wantKeepAlive => true; // 🚀 保持页面存活状态
  
  @override
  void initState() {
    super.initState();
    
    // 🚀 优化：简化初始化逻辑，直接检查数据状态
    final settingState = context.read<SettingBloc>().state;
    
    AppLogger.i('NovelSettingSidebar', '📊 初始化设定侧边栏 - 小说ID: ${widget.novelId}');
    AppLogger.i('NovelSettingSidebar', '   组状态: ${settingState.groupsStatus}, 组数量: ${settingState.groups.length}');
    AppLogger.i('NovelSettingSidebar', '   条目状态: ${settingState.itemsStatus}, 条目数量: ${settingState.items.length}');
    
    // 🚀 优化：更积极的加载策略，即使状态为loading也可以确保数据最新
    if (settingState.groupsStatus == SettingStatus.initial ||
        settingState.groupsStatus == SettingStatus.failure ||
        settingState.groups.isEmpty) {
      AppLogger.i('NovelSettingSidebar', '🚀 立即加载设定组');
      context.read<SettingBloc>().add(LoadSettingGroups(widget.novelId));
    }
    
    if (settingState.itemsStatus == SettingStatus.initial ||
        settingState.itemsStatus == SettingStatus.failure ||
        settingState.items.isEmpty) {
      AppLogger.i('NovelSettingSidebar', '🚀 立即加载设定条目用于引用检测');
      context.read<SettingBloc>().add(LoadSettingItems(novelId: widget.novelId));
    }
    
    // 🚀 新增：如果数据已经存在，立即通知场景编辑器可以开始引用检测
    if (settingState.itemsStatus == SettingStatus.success && settingState.items.isNotEmpty) {
      AppLogger.i('NovelSettingSidebar', '✅ 设定数据已就绪，条目数量: ${settingState.items.length}');
    }
  }
  
  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }
  
  // 切换分组模式
  void _toggleGroupingMode(String mode) {
    setState(() {
      _groupingMode = mode;
    });
    AppLogger.i('NovelSettingSidebar', '切换分组模式: $mode');
  }
  
  // 切换设定类型展开/折叠状态
  void _toggleTypeExpansion(String typeValue) {
    setState(() {
      if (_expandedTypeIds.contains(typeValue)) {
        _expandedTypeIds.remove(typeValue);
        AppLogger.i('NovelSettingSidebar', '折叠设定类型: $typeValue');
      } else {
        _expandedTypeIds.add(typeValue);
        AppLogger.i('NovelSettingSidebar', '展开设定类型: $typeValue');
      }
    });
  }
  
  // 切换设定组展开/折叠状态
  void _toggleGroupExpansion(String groupId) {
    final settingState = context.read<SettingBloc>().state;
    final group = settingState.groups.firstWhere(
      (g) => g.id == groupId,
      orElse: () => SettingGroup(name: '未知设定组'),
    );
    
    setState(() {
      if (_expandedGroupIds.contains(groupId)) {
        _expandedGroupIds.remove(groupId);
        AppLogger.i('NovelSettingSidebar', '折叠设定组: ${group.name}');
      } else {
        _expandedGroupIds.add(groupId);
        AppLogger.i('NovelSettingSidebar', '展开设定组: ${group.name}, 组内条目ID数量: ${group.itemIds?.length ?? 0}, 实际条目数量: ${settingState.items.length}');
        
        // 检查是否有任何组内条目未加载
        final missingItems = <String>[];
        if (group.itemIds != null) {
          for (final itemId in group.itemIds!) {
            if (!settingState.items.any((item) => item.id == itemId)) {
              missingItems.add(itemId);
            }
          }
        }
        
        // 如果有未加载的条目，重新加载所有条目
        if (missingItems.isNotEmpty) {
          AppLogger.i('NovelSettingSidebar', '发现未加载的条目: $missingItems, 重新加载所有条目');
          context.read<SettingBloc>().add(LoadSettingItems(
            novelId: widget.novelId,
          ));
        }
      }
    });
  }
  
  // 创建新设定组
  void _createSettingGroup() {
    final settingBloc = context.read<SettingBloc>();
    FloatingSettingDialogs.showSettingGroup(
      context: context,
      novelId: widget.novelId,
      onSave: (group) {
        settingBloc.add(CreateSettingGroup(
          novelId: widget.novelId,
          group: group,
        ));
      },
    );
  }
  
  // 编辑设定组
  void _editSettingGroup(String groupId) {
    final settingBloc = context.read<SettingBloc>();
    final group = settingBloc.state.groups.firstWhere(
      (g) => g.id == groupId,
      orElse: () => SettingGroup(name: '未知设定组'),
    );
    
    FloatingSettingDialogs.showSettingGroup(
      context: context,
      novelId: widget.novelId,
      group: group,
      onSave: (updatedGroup) {
        settingBloc.add(UpdateSettingGroup(
          novelId: widget.novelId,
          groupId: groupId,
          group: updatedGroup,
        ));
      },
    );
  }
  
  // 删除设定组
  void _deleteSettingGroup(String groupId) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('确认删除'),
        content: const Text('确定要删除这个设定组吗？组内的设定条目将不会被删除。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              context.read<SettingBloc>().add(DeleteSettingGroup(
                novelId: widget.novelId,
                groupId: groupId,
              ));
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: WebTheme.error,
              foregroundColor: WebTheme.white,
            ),
            child: const Text('删除'),
          ),
        ],
      ),
    );
  }
  
  // 创建新设定条目
  void _createSettingItem({String? groupId}) {
    // 如果没有指定groupId，则尝试使用第一个可用的设定组
    String? defaultGroupId = groupId;
    if (defaultGroupId == null) {
      final settingState = context.read<SettingBloc>().state;
      if (settingState.groups.isNotEmpty) {
        defaultGroupId = settingState.groups.first.id;
      }
    }
    
    FloatingNovelSettingDetail.show(
      context: context,
      novelId: widget.novelId,
      groupId: defaultGroupId,
      isEditing: true,
      onSave: _saveSettingItem,
      onCancel: () {
        // 取消回调
      },
    );
  }
  
  // 编辑设定条目
  // void _editSettingItem(String itemId, {String? groupId}) {
  //   FloatingNovelSettingDetail.show(
  //     context: context,
  //     itemId: itemId,
  //     novelId: widget.novelId,
  //     groupId: groupId,
  //     isEditing: true,
  //     onSave: _saveSettingItem,
  //     onCancel: () {
  //       // 取消回调
  //     },
  //   );
  // }
  
  // 查看设定条目
  void _viewSettingItem(String itemId, {String? groupId}) {
    FloatingNovelSettingDetail.show(
      context: context,
      itemId: itemId,
      novelId: widget.novelId,
      groupId: groupId,
      isEditing: false,
      onSave: _saveSettingItem,
      onCancel: () {
        // 取消回调
      },
    );
  }
  
  // 删除设定条目
  // void _deleteSettingItem(String itemId) {
  //   showDialog(
  //     context: context,
  //     builder: (context) => AlertDialog(
  //       title: const Text('确认删除'),
  //       content: const Text('确定要删除这个设定条目吗？此操作不可撤销。'),
  //       actions: [
  //         TextButton(
  //           onPressed: () => Navigator.of(context).pop(),
  //           child: const Text('取消'),
  //         ),
  //         ElevatedButton(
  //           onPressed: () {
  //             Navigator.of(context).pop();
  //             context.read<SettingBloc>().add(DeleteSettingItem(
  //               novelId: widget.novelId,
  //               itemId: itemId,
  //             ));
  //           },
  //           style: ElevatedButton.styleFrom(
  //             backgroundColor: WebTheme.error,
  //             foregroundColor: WebTheme.white,
  //           ),
  //           child: const Text('删除'),
  //         ),
  //       ],
  //     ),
  //   );
  // }
  
  // 保存设定条目
  void _saveSettingItem(NovelSettingItem item, String? groupId) {
    AppLogger.i('NovelSettingSidebar', '保存设定条目: ${item.name}, ID=${item.id}, 传入组ID=${groupId}');
    
    if (item.id == null) {
      // 创建新条目
      final settingBloc = context.read<SettingBloc>();
      
      if (groupId != null) {
        // 使用传入的组ID创建并添加到组中
        settingBloc.add(CreateSettingItemAndAddToGroup(
          novelId: widget.novelId,
          item: item,
          groupId: groupId,
        ));
        
        AppLogger.i('NovelSettingSidebar', '使用组ID创建并添加到组: $groupId');
      } else {
        // 无组ID时直接创建条目
        settingBloc.add(CreateSettingItem(
          novelId: widget.novelId,
          item: item,
        ));
        
        AppLogger.i('NovelSettingSidebar', '无组ID创建');
      }
    } else {
      // 更新现有条目
      final settingBloc = context.read<SettingBloc>();
      final state = settingBloc.state;
      settingBloc.add(UpdateSettingItem(
        novelId: widget.novelId,
        itemId: item.id!,
        item: item,
      ));

      // 处理组变更：对比旧组与新组，执行移除/添加
      final String? oldGroupId = _findGroupIdByItemId(item.id!, state);
      if (oldGroupId != groupId) {
        AppLogger.i('NovelSettingSidebar', '检测到组变更: old=$oldGroupId -> new=$groupId');
        if (oldGroupId != null) {
          settingBloc.add(RemoveItemFromGroup(
            novelId: widget.novelId,
            groupId: oldGroupId,
            itemId: item.id!,
          ));
          AppLogger.i('NovelSettingSidebar', '已从旧组移除: $oldGroupId');
        }
        if (groupId != null) {
          settingBloc.add(AddItemToGroup(
            novelId: widget.novelId,
            groupId: groupId,
            itemId: item.id!,
          ));
          AppLogger.i('NovelSettingSidebar', '已添加到新组: $groupId');
        }
      } else {
        AppLogger.i('NovelSettingSidebar', '组未变更，跳过组更新');
      }
    }
  }
  
  // 激活或取消激活设定组
  void _toggleGroupActive(String groupId, bool currentIsActive) {
    context.read<SettingBloc>().add(SetGroupActiveContext(
      novelId: widget.novelId,
      groupId: groupId,
      isActive: !currentIsActive,
    ));
  }
  
  // 搜索设定条目
  void _searchItems(String searchTerm) {
    if (searchTerm.isEmpty) {
      // 如果搜索词为空，加载所有条目
      context.read<SettingBloc>().add(LoadSettingItems(
        novelId: widget.novelId,
      ));
    } else {
      // 搜索条目
      context.read<SettingBloc>().add(LoadSettingItems(
        novelId: widget.novelId,
        name: searchTerm,
      ));
    }
  }
  
  // 根据设定条目ID查找所属的设定组ID
  String? _findGroupIdByItemId(String itemId, SettingState state) {
    for (final group in state.groups) {
      if (group.itemIds != null && group.itemIds!.contains(itemId)) {
        return group.id;
      }
    }
    return null;
  }
  
  @override
  Widget build(BuildContext context) {
    super.build(context); // 🚀 必须调用父类的build方法
    return Material(
      color: WebTheme.getSurfaceColor(context),
      child: Container(
        color: WebTheme.getSurfaceColor(context),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 分组切换按钮
            _buildGroupingToggle(context),
            
            // 搜索和操作栏
            _buildSearchBar(context),
            
            // 内容区域
            Expanded(
              child: BlocBuilder<SettingBloc, SettingState>(
                buildWhen: (previous, current) {
                  // 仅当与列表相关的数据发生变化时才重建，避免无关状态变更导致的重建
                  final itemsChanged = !identical(previous.items, current.items);
                  final groupsChanged = !identical(previous.groups, current.groups);
                  final selectedGroupChanged = previous.selectedGroupId != current.selectedGroupId;
                  return itemsChanged || groupsChanged || selectedGroupChanged;
                },
                builder: (context, state) {
                  // 🚀 新增：设定数据加载状态日志
                  AppLogger.i('NovelSettingSidebar', '🔄 构建设定侧边栏');
                  AppLogger.d('NovelSettingSidebar', '📊 设定条目数量: ${state.items.length}');
                  AppLogger.d('NovelSettingSidebar', '📁 设定组数量: ${state.groups.length}');
                  
                  // 🔧 修复：数量异常提醒
                  if (state.items.length > 100) {
                    AppLogger.w('NovelSettingSidebar', '⚠️ 设定数量异常多: ${state.items.length}个，请检查是否为历史恢复导致');
                  }
                  
                  if (state.items.isNotEmpty) {
                    AppLogger.d('NovelSettingSidebar', '📋 设定条目列表:');
                    for (int i = 0; i < state.items.length && i < 10; i++) {
                      final item = state.items[i];
                      AppLogger.d('NovelSettingSidebar', '  [$i] ${item.name} (ID: ${item.id})');
                    }
                    if (state.items.length > 10) {
                      AppLogger.d('NovelSettingSidebar', '  ... 还有 ${state.items.length - 10} 个设定条目');
                    }
                  }
                  
                  if (state.groupsStatus == SettingStatus.loading && state.groups.isEmpty) {
                    return _buildLoadingState(context);
                  }
                  
                  if (state.groupsStatus == SettingStatus.failure) {
                    return _buildErrorState(context, state.error);
                  }
                  
                  if (state.groups.isEmpty && state.items.isEmpty) {
                    return _buildEmptyState(context);
                  }

                  return _buildSettingList(context, state);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
  
  // 构建分组切换按钮
  Widget _buildGroupingToggle(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 6.0),
      decoration: BoxDecoration(
        color: WebTheme.getSurfaceColor(context),
        border: Border(
          bottom: BorderSide(
            color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
            width: 1.0,
          ),
        ),
      ),
      child: Row(
        children: [
          // 按设定分类分组按钮
          Expanded(
            child: GestureDetector(
              onTap: () => _toggleGroupingMode('type'),
              child: Container(
                height: 28,
                decoration: BoxDecoration(
                  color: _groupingMode == 'type' 
                      ? WebTheme.getPrimaryColor(context)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                    color: _groupingMode == 'type' 
                        ? WebTheme.getPrimaryColor(context)
                        : WebTheme.getSecondaryBorderColor(context),
                    width: 1,
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.category,
                      size: 14,
                      color: _groupingMode == 'type' 
                          ? WebTheme.white
                          : WebTheme.getSecondaryTextColor(context),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '按分类',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: _groupingMode == 'type' 
                            ? WebTheme.white
                            : WebTheme.getSecondaryTextColor(context),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          
          const SizedBox(width: 8),
          
          // 按设定组分组按钮
          Expanded(
            child: GestureDetector(
              onTap: () => _toggleGroupingMode('group'),
              child: Container(
                height: 28,
                decoration: BoxDecoration(
                  color: _groupingMode == 'group' 
                      ? WebTheme.getPrimaryColor(context)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                    color: _groupingMode == 'group' 
                        ? WebTheme.getPrimaryColor(context)
                        : WebTheme.getSecondaryBorderColor(context),
                    width: 1,
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.folder,
                      size: 14,
                      color: _groupingMode == 'group' 
                          ? WebTheme.white
                          : WebTheme.getSecondaryTextColor(context),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '按组别',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: _groupingMode == 'group' 
                            ? WebTheme.white
                            : WebTheme.getSecondaryTextColor(context),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  // 构建搜索和操作栏
  Widget _buildSearchBar(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 8.0),
      decoration: BoxDecoration(
        color: WebTheme.getBackgroundColor(context),
        border: Border(
          bottom: BorderSide(
            color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
            width: 1.0,
          ),
        ),
      ),
      child: Row(
        children: [
          // 搜索框
          Expanded(
            child: AppSearchField(
              controller: _searchController,
              hintText: '搜索设定...',
              height: 34,
              fillColor: WebTheme.getBackgroundColor(context),
              onChanged: (value) {
                if (value.isEmpty) {
                  _searchItems('');
                }
              },
              onSubmitted: _searchItems,
              onClear: () {
                _searchController.clear();
                _searchItems('');
              },
            ),
          ),
          const SizedBox(width: 4),
          // 🔧 新增：设定数量指示器
          BlocBuilder<SettingBloc, SettingState>(
            buildWhen: (previous, current) => previous.items.length != current.items.length,
            builder: (context, settingState) {
              if (settingState.items.isNotEmpty) {
                return Container(
                  height: 34,
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  margin: const EdgeInsets.only(right: 4),
                  decoration: BoxDecoration(
                    color: settingState.items.length > 50 
                        ? Colors.orange.withOpacity(0.1)
                        : WebTheme.isDarkMode(context) 
                            ? WebTheme.darkGrey100.withOpacity(0.3)
                            : WebTheme.grey100,
                    borderRadius: BorderRadius.circular(6),
                    border: settingState.items.length > 50 
                        ? Border.all(color: Colors.orange.withOpacity(0.3), width: 1)
                        : Border.all(color: WebTheme.getSecondaryBorderColor(context), width: 1),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.settings_outlined,
                        size: 14,
                        color: settingState.items.length > 50
                            ? Colors.orange.shade700
                            : WebTheme.getSecondaryTextColor(context),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${settingState.items.length}',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: settingState.items.length > 50
                              ? Colors.orange.shade700
                              : WebTheme.getSecondaryTextColor(context),
                        ),
                      ),
                    ],
                  ),
                );
              }
              return const SizedBox.shrink();
            },
          ),
          // 新建条目按钮
          SizedBox(
            height: 34,
            child: OutlinedButton.icon(
              onPressed: () => _createSettingItem(),
              icon: const Icon(Icons.add, size: 14),
              label: const Text('新建条目'),
              style: OutlinedButton.styleFrom(
                foregroundColor: WebTheme.getTextColor(context),
                backgroundColor: WebTheme.getBackgroundColor(context),
                side: BorderSide(
                  color: WebTheme.getTextColor(context),
                  width: 1.0,
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 6,
                  vertical: 0,
                ),
                textStyle: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(6),
                ),
              ),
            ),
          ),
          const SizedBox(width: 4),
          // 新建组按钮
          SizedBox(
            height: 34,
            child: OutlinedButton.icon(
              onPressed: _createSettingGroup,
              icon: const Icon(Icons.create_new_folder_outlined, size: 14),
              label: const Text('新建组'),
              style: OutlinedButton.styleFrom(
                foregroundColor: WebTheme.getSecondaryTextColor(context),
                backgroundColor: WebTheme.getBackgroundColor(context),
                side: BorderSide(
                  color: WebTheme.getSecondaryTextColor(context),
                  width: 1.0,
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 6,
                  vertical: 0,
                ),
                textStyle: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(6),
                ),
              ),
            ),
          ),
          const SizedBox(width: 2),
          // 设置按钮
          IconButton(
            onPressed: () {
              // TODO: 实现设定设置功能
            },
            icon: Icon(
              Icons.settings_outlined,
              size: 16,
              color: WebTheme.getSecondaryTextColor(context),
            ),
            tooltip: '设定设置',
            splashRadius: 16,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(
              minWidth: 28,
              minHeight: 28,
            ),
          ),
        ],
      ),
    );
  }
  
  // 构建加载状态
  Widget _buildLoadingState(BuildContext context) {
    return const Center(
      child: CircularProgressIndicator(),
    );
  }
  
  // 构建错误状态
  Widget _buildErrorState(BuildContext context, String? error) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline,
            color: WebTheme.error,
            size: 48,
          ),
          const SizedBox(height: 16),
          Text(
            '加载设定数据失败',
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          if (error != null)
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                error,
                style: TextStyle(
                  color: WebTheme.getSecondaryTextColor(context),
                ),
                textAlign: TextAlign.center,
              ),
            ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () {
              context.read<SettingBloc>().add(LoadSettingGroups(widget.novelId));
            },
            child: const Text('重试'),
          ),
        ],
      ),
    );
  }
  
  // 构建空状态
  Widget _buildEmptyState(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(20.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '设定库为空',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.5,
              color: WebTheme.getTextColor(context),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '设定库存储您小说世界的信息，包括角色、地点、物品及更多设定内容。',
            style: TextStyle(
              color: WebTheme.getSecondaryTextColor(context),
              fontSize: 14,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 12),
          InkWell(
            onTap: _createSettingGroup,
            child: Text(
              '→ 点击创建第一个设定组',
              style: TextStyle(
                color: WebTheme.getTextColor(context),
                fontSize: 14,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          const SizedBox(height: 8),
          InkWell(
            onTap: () => _createSettingItem(),
            child: Text(
              '→ 点击创建第一个设定条目',
              style: TextStyle(
                color: WebTheme.getTextColor(context),
                fontSize: 14,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  // 构建设定列表（树状结构）
  Widget _buildSettingList(BuildContext context, SettingState state) {
    final isSearching = _searchController.text.isNotEmpty;
    
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        // 搜索结果
        if (isSearching && state.items.isNotEmpty)
          ..._buildSearchResultItems(context, state.items),
        
        // 如果正在搜索且没有结果
        if (isSearching && state.items.isEmpty)
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              '没有找到匹配"${_searchController.text}"的设定条目',
              style: TextStyle(
                fontSize: 14,
                color: WebTheme.getSecondaryTextColor(context),
                fontStyle: FontStyle.italic,
              ),
            ),
          ),
          
        // 不在搜索时根据分组模式显示内容
        if (!isSearching)
          ..._buildGroupedContent(context, state),
      ],
    );
  }
  
  // 构建分组内容
  List<Widget> _buildGroupedContent(BuildContext context, SettingState state) {
    if (_groupingMode == 'type') {
      // 按设定分类分组
      return _buildTypeGroupedItems(context, state.items);
    } else {
      // 按设定组分组
      return state.groups.map((group) => 
        _buildSettingGroupItem(context, group, state.items)).toList();
    }
  }
  
  // 构建按设定类型分组的列表
  List<Widget> _buildTypeGroupedItems(BuildContext context, List<NovelSettingItem> allItems) {
    // 按类型分组设定条目
    final Map<String, List<NovelSettingItem>> typeGroups = {};
    
    for (final item in allItems) {
      final type = item.type ?? 'OTHER';
      if (!typeGroups.containsKey(type)) {
        typeGroups[type] = [];
      }
      typeGroups[type]!.add(item);
    }
    
    // 按类型显示名称排序
    final sortedTypes = typeGroups.keys.toList()
      ..sort((a, b) {
        final typeA = SettingType.fromValue(a);
        final typeB = SettingType.fromValue(b);
        return typeA.displayName.compareTo(typeB.displayName);
      });
    
    return sortedTypes.map((typeValue) {
      final typeEnum = SettingType.fromValue(typeValue);
      final items = typeGroups[typeValue]!;
      // 按名称排序条目
      items.sort((a, b) => a.name.compareTo(b.name));
      
      return _buildSettingTypeItem(context, typeEnum, items);
    }).toList();
  }
  
  // 构建设定类型项目
  Widget _buildSettingTypeItem(BuildContext context, SettingType type, List<NovelSettingItem> items) {
    final isExpanded = _expandedTypeIds.contains(type.value);
    
    return Container(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
            width: 1.0,
          ),
        ),
      ),
      child: Column(
        children: [
          // 设定类型标题行
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey50,
              border: Border(
                top: BorderSide(
                  color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
                  width: 1.0,
                ),
                bottom: BorderSide(
                  color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
                  width: 1.0,
                ),
              ),
            ),
            child: InkWell(
              onTap: () => _toggleTypeExpansion(type.value),
              child: Row(
                children: [
                  // 类型图标
                  (items.isNotEmpty && items.first.imageUrl != null && items.first.imageUrl!.isNotEmpty)
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: Image.network(
                            items.first.imageUrl!,
                            width: 24,
                            height: 24,
                            fit: BoxFit.cover,
                            errorBuilder: (ctx, err, st) => Icon(
                              _getTypeIconData(type),
                              size: 24,
                              color: WebTheme.getSecondaryTextColor(context),
                            ),
                            loadingBuilder: (ctx, child, loading) {
                              if (loading == null) return child;
                              return Center(
                                child: SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                ),
                              );
                            },
                          ),
                        )
                      : Icon(
                          _getTypeIconData(type),
                          size: 24,
                          color: WebTheme.getSecondaryTextColor(context),
                        ),
                  const SizedBox(width: 8),
                  // 设定类型名称
                  Expanded(
                    child: Text(
                      type.displayName,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: WebTheme.getTextColor(context),
                      ),
                    ),
                  ),
                  // 右侧控制区域
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // 条目数量
                      Text(
                        '${items.length} entries',
                        style: TextStyle(
                          fontSize: 12,
                          color: WebTheme.getSecondaryTextColor(context),
                        ),
                      ),
                      const SizedBox(width: 4),
                      // 创建该类型设定按钮
                      GestureDetector(
                        onTap: () => _createSettingItemWithType(type),
                        child: Container(
                          width: 24,
                          height: 24,
                          decoration: BoxDecoration(
                            color: Colors.transparent,
                            borderRadius: BorderRadius.circular(4),
                            border: Border.all(
                              color: Colors.transparent,
                              width: 1,
                            ),
                          ),
                          child: Icon(
                            Icons.add,
                            size: 14,
                            color: WebTheme.getSecondaryTextColor(context),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      // 展开/折叠图标
                      Icon(
                        isExpanded ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_right,
                        size: 16,
                        color: WebTheme.getSecondaryTextColor(context),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          
          // 如果展开，显示该类型的设定条目
          if (isExpanded)
            ..._buildTypeSettingItems(context, items),
        ],
      ),
    );
  }
  
  // 构建类型分组下的设定条目列表
  List<Widget> _buildTypeSettingItems(BuildContext context, List<NovelSettingItem> items) {
    if (items.isEmpty) {
      return [
        Container(
          padding: const EdgeInsets.all(16),
          child: Text(
            '该类型下暂无设定条目',
            style: TextStyle(
              fontSize: 13,
              color: WebTheme.getSecondaryTextColor(context),
              fontStyle: FontStyle.italic,
            ),
          ),
        ),
      ];
    }
    
    return items.map((item) => _buildSettingItemTile(context, item, null)).toList();
  }
  
  // 创建指定类型的设定条目
  void _createSettingItemWithType(SettingType type) {
    FloatingNovelSettingDetail.show(
      context: context,
      novelId: widget.novelId,
      isEditing: true,
      prefilledType: type.value, // 预设指定的类型
      onSave: _saveSettingItem,
      onCancel: () {
        // 取消操作的回调
      },
    );
  }
  
  // 构建搜索结果的设定条目列表
  List<Widget> _buildSearchResultItems(BuildContext context, List<NovelSettingItem> items) {
    return [
      // 搜索结果标题
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
        child: Text(
          '搜索结果',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: WebTheme.getSecondaryTextColor(context),
          ),
        ),
      ),
      // 搜索结果列表 - 查找每个条目所属的组ID
      ...items.map((item) {
        final state = context.read<SettingBloc>().state;
        final groupId = item.id != null ? _findGroupIdByItemId(item.id!, state) : null;
        return _buildSettingItemTile(context, item, groupId);
      }),
    ];
  }

  // 构建设定组项目
  Widget _buildSettingGroupItem(BuildContext context, SettingGroup group, List<NovelSettingItem> allItems) {
    final isExpanded = _expandedGroupIds.contains(group.id);
    
    // 调试信息
    if (isExpanded && group.id != null) {
      AppLogger.i('NovelSettingSidebar', '展开组 ${group.name}(${group.id}) - 组内条目IDs: ${group.itemIds}, 所有条目数量: ${allItems.length}');
    }
    
    // 筛选属于该组的条目
    final List<NovelSettingItem> groupItems = [];
    if (group.itemIds != null && group.itemIds!.isNotEmpty) {
      for (final itemId in group.itemIds!) {
        final item = allItems.firstWhere(
          (item) => item.id == itemId,
          orElse: () => NovelSettingItem(
            id: itemId, 
            name: "加载中...", 
            content: ""
          ),
        );
        groupItems.add(item);
      }
      
      // 按名称排序
      groupItems.sort((a, b) => a.name.compareTo(b.name));
      
      // 调试信息
      if (isExpanded) {
        AppLogger.i('NovelSettingSidebar', '筛选后组内条目数量: ${groupItems.length}');
      }
    }
    
    return Container(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
            width: 1.0,
          ),
        ),
      ),
      child: Column(
        children: [
          // 设定组标题行 - 重新设计样式
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey50,
              border: Border(
                top: BorderSide(
                  color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
                  width: 1.0,
                ),
                bottom: BorderSide(
                  color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
                  width: 1.0,
                ),
              ),
            ),
            child: InkWell(
              onTap: () {
                if (group.id != null) {
                  _toggleGroupExpansion(group.id!);
                }
              },
              child: Row(
                children: [
                  // 设定组名称
                  Expanded(
                    child: Text(
                      group.name,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                                                 color: WebTheme.getTextColor(context),
                      ),
                    ),
                  ),
                  // 右侧控制区域
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // 条目数量
                      Text(
                        '${groupItems.length} entries',
                        style: TextStyle(
                          fontSize: 12,
                          color: WebTheme.getSecondaryTextColor(context),
                        ),
                      ),
                      const SizedBox(width: 4),
                      // 添加按钮
                      if (group.id != null)
                        GestureDetector(
                          onTap: () => _createSettingItem(groupId: group.id),
                          child: Container(
                            width: 24,
                            height: 24,
                            decoration: BoxDecoration(
                              color: Colors.transparent,
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(
                                color: Colors.transparent,
                                width: 1,
                              ),
                            ),
                            child: Icon(
                              Icons.add,
                              size: 14,
                              color: WebTheme.getSecondaryTextColor(context),
                            ),
                          ),
                        ),
                      const SizedBox(width: 4),
                      // 展开/折叠图标
                      Icon(
                        isExpanded ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_right,
                        size: 16,
                        color: WebTheme.getSecondaryTextColor(context),
                      ),
                      // 设定组菜单按钮
                      if (group.id != null)
                        _buildGroupMenuButton(context, group),
                    ],
                  ),
                ],
              ),
            ),
          ),
          
          // 如果展开，显示该组的设定条目
          if (isExpanded && group.id != null)
            ..._buildSettingItems(context, groupItems, group.id!),
        ],
      ),
    );
  }

  // 构建设定条目列表
  List<Widget> _buildSettingItems(BuildContext context, List<NovelSettingItem> items, String groupId) {
    if (items.isEmpty) {
      return [
        Container(
          padding: const EdgeInsets.all(16),
          child: Text(
            '该设定组下暂无条目',
            style: TextStyle(
              fontSize: 13,
              color: WebTheme.getSecondaryTextColor(context),
              fontStyle: FontStyle.italic,
            ),
          ),
        ),
      ];
    }
    
    return items.map((item) => _buildSettingItemTile(context, item, groupId)).toList();
  }
  
  // 构建设定条目项 - 重新设计为更简洁的样式
  Widget _buildSettingItemTile(BuildContext context, NovelSettingItem item, String? groupId) {
    return Container(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey100,
            width: 1.0,
          ),
        ),
      ),
      child: InkWell(
        onTap: () {
          if (item.id != null) {
            _viewSettingItem(item.id!, groupId: groupId);
          }
        },
        child: Container(
          padding: const EdgeInsets.all(8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // 设定类型图标
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey100 : WebTheme.grey100,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.white,
                    width: 2,
                  ),
                ),
                child: (item.imageUrl != null && item.imageUrl!.isNotEmpty)
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(6),
                        child: Image.network(
                          item.imageUrl!,
                          width: 24,
                          height: 24,
                          fit: BoxFit.cover,
                          errorBuilder: (ctx, err, st) => Icon(
                            _getTypeIconData(SettingType.fromValue(item.type ?? 'OTHER')),
                            size: 24,
                            color: WebTheme.getSecondaryTextColor(context),
                          ),
                          loadingBuilder: (ctx, child, loading) {
                            if (loading == null) return child;
                            return Center(
                              child: SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              ),
                            );
                          },
                        ),
                      )
                    : Icon(
                        _getTypeIconData(SettingType.fromValue(item.type ?? 'OTHER')),
                        size: 24,
                        color: WebTheme.getSecondaryTextColor(context),
                      ),
              ),
              const SizedBox(width: 12),
              
              // 内容区域
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 标题行
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            item.name.isNotEmpty ? item.name : 'Unnamed Entry',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              color: item.name.isNotEmpty 
                                ? WebTheme.getTextColor(context)
                                : WebTheme.getSecondaryTextColor(context),
                              fontStyle: item.name.isNotEmpty ? FontStyle.normal : FontStyle.italic,
                            ),
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    
                    // 描述内容
                    if (item.description != null && item.description!.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          item.description!,
                          style: TextStyle(
                            fontSize: 12,
                            color: WebTheme.getSecondaryTextColor(context),
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    
                    // 标签行（放在最后）
                    if (item.tags != null && item.tags!.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Wrap(
                          spacing: 4,
                          runSpacing: 4,
                          children: item.tags!.map((tag) => Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: WebTheme.isDarkMode(context) ? WebTheme.darkGrey200 : WebTheme.grey200,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              tag,
                              style: TextStyle(
                                fontSize: 10,
                                color: WebTheme.getTextColor(context),
                              ),
                            ),
                          )).toList(),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 获取类型图标
  IconData _getTypeIconData(SettingType type) {
    switch (type) {
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

  // 构建设定组菜单按钮
  Widget _buildGroupMenuButton(BuildContext context, SettingGroup group) {
    if (group.id == null) return const SizedBox.shrink();
    
    return CustomDropdown(
      width: 200,
      align: 'right',
      trigger: Icon(
        Icons.more_vert,
        size: 16,
        color: WebTheme.getSecondaryTextColor(context),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          DropdownItem(
            icon: Icons.edit,
            label: '编辑设定组',
            onTap: () async {
              _editSettingGroup(group.id!);
            },
          ),
          DropdownItem(
            icon: group.isActiveContext == true ? Icons.star : Icons.star_border,
            label: group.isActiveContext == true ? '取消活跃状态' : '设为活跃上下文',
            onTap: () async {
              _toggleGroupActive(group.id!, group.isActiveContext ?? false);
            },
          ),
          DropdownItem(
            icon: Icons.add_circle_outline,
            label: '添加设定条目到此组',
            onTap: () async {
              _createSettingItem(groupId: group.id);
            },
          ),
          const DropdownDivider(),
          DropdownItem(
            icon: Icons.delete_outline,
            label: '删除设定组',
            isDangerous: true,
            onTap: () async {
              _deleteSettingGroup(group.id!);
            },
          ),
        ],
      ),
    );
  }
  
  // 构建设定条目菜单按钮
  // Widget _buildItemMenuButton(BuildContext context, NovelSettingItem item, String? groupId) { return const SizedBox.shrink(); }
  
  // 根据设定条目类型构建对应图标
  // Widget _buildTypeIcon(String type) { return const SizedBox.shrink(); }

  // 根据设定条目类型获取对应颜色
  // Color _getTypeColor(SettingType type) {
  //   switch (type) {
  //     case SettingType.character:
  //       return WebTheme.getPrimaryColor(context);
  //     case SettingType.location:
  //       return WebTheme.getSecondaryColor(context);
  //     case SettingType.item:
  //       return WebTheme.getTextColor(context);
  //     case SettingType.lore:
  //       return WebTheme.getSecondaryTextColor(context);
  //     case SettingType.event:
  //       return WebTheme.error;
  //     case SettingType.concept:
  //       return WebTheme.getOnSurfaceColor(context);
  //     case SettingType.faction:
  //       return WebTheme.getTextColor(context);
  //     case SettingType.creature:
  //       return WebTheme.getSecondaryTextColor(context);
  //     case SettingType.magicSystem:
  //       return WebTheme.getPrimaryColor(context);
  //     case SettingType.technology:
  //       return WebTheme.getSecondaryTextColor(context);
  //     case SettingType.culture:
  //       return Colors.deepOrange;
  //     case SettingType.history:
  //       return Colors.brown;
  //     case SettingType.organization:
  //       return Colors.indigo;
  //     case SettingType.worldview:
  //       return Colors.purple;
  //     case SettingType.pleasurePoint:
  //       return Colors.redAccent;
  //     case SettingType.anticipationHook:
  //       return Colors.teal;
  //     case SettingType.theme:
  //       return Colors.blueGrey;
  //     case SettingType.tone:
  //       return Colors.amber;
  //     case SettingType.style:
  //       return Colors.cyan;
  //     case SettingType.trope:
  //       return Colors.pink;
  //     case SettingType.plotDevice:
  //       return Colors.green;
  //     case SettingType.powerSystem:
  //       return Colors.orange;
  //     case SettingType.timeline:
  //       return Colors.blue;
  //     case SettingType.religion:
  //       return Colors.deepPurple;
  //     case SettingType.politics:
  //       return Colors.red;
  //     case SettingType.economy:
  //       return Colors.lightGreen;
  //     case SettingType.geography:
  //       return Colors.lightBlue;
  //     default:
  //       return WebTheme.getSecondaryTextColor(context);
  //   }
  // }
} 