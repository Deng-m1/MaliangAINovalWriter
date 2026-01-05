import 'package:ainoval/models/user_ai_model_config_model.dart';
import 'package:ainoval/models/ai_model_group.dart';
import 'package:ainoval/models/model_info.dart'; // Import ModelInfo
import 'package:ainoval/services/api_service/repositories/user_ai_model_config_repository.dart';
import 'package:ainoval/services/model_listing/model_listing_service.dart';
import 'package:ainoval/utils/logger.dart';
import 'package:bloc/bloc.dart';
import 'package:collection/collection.dart'; // For firstWhereOrNull
import 'package:equatable/equatable.dart';
import 'package:flutter/foundation.dart'; // For ValueGetter

part 'ai_config_event.dart';
part 'ai_config_state.dart';

class AiConfigBloc extends Bloc<AiConfigEvent, AiConfigState> {
  AiConfigBloc({required UserAIModelConfigRepository repository})
      : _repository = repository,
        super(const AiConfigState()) {
    on<LoadAiConfigs>(_onLoadAiConfigs);
    on<ResetAiConfigs>(_onResetAiConfigs);
    on<LoadAvailableProviders>(_onLoadAvailableProviders);
    on<LoadModelsForProvider>(_onLoadModelsForProvider);
    on<AddAiConfig>(_onAddAiConfig);
    on<UpdateAiConfig>(_onUpdateAiConfig);
    on<DeleteAiConfig>(_onDeleteAiConfig);
    on<ValidateAiConfig>(_onValidateAiConfig);
    on<SetDefaultAiConfig>(_onSetDefaultAiConfig);
    on<ClearProviderModels>(_onClearProviderModels);
    on<GetProviderDefaultConfig>(_onGetProviderDefaultConfig);
    on<LoadApiKeyForConfig>(_onLoadApiKeyForConfig);
    on<LoadProviderCapability>(_onLoadProviderCapability);
    on<TestApiKey>(_onTestApiKey);
    on<ClearApiKeyTestError>(_onClearApiKeyTestError);
    on<ClearModelsCache>(_onClearModelsCache);
    on<AddCustomModelAndValidate>(_onAddCustomModelAndValidate);
    on<SetToolDefaultAiConfig>(_onSetToolDefaultAiConfig);
    on<LoadModelsDirect>(_onLoadModelsDirect);
  }
  final UserAIModelConfigRepository _repository;
  
  // 添加缓存机制
  DateTime? _lastConfigsLoadTime;
  static const Duration _cacheValidDuration = Duration(minutes: 5);
  // 记录上一次加载配置对应的用户，用于跨用户时强制刷新
  String? _lastLoadedUserId;
  
  // 添加模型列表缓存机制
  Map<String, DateTime> _modelsCacheTime = {};
  static const Duration _modelsCacheValidDuration = Duration(minutes: 10);
  
  // 添加提供商列表缓存机制
  DateTime? _lastProvidersLoadTime;
  static const Duration _providersCacheDuration = Duration(hours: 1);
  
  bool get _shouldRefreshConfigs {
    if (_lastConfigsLoadTime == null) return true;
    return DateTime.now().difference(_lastConfigsLoadTime!) > _cacheValidDuration;
  }
  
  bool get _shouldRefreshProviders {
    if (_lastProvidersLoadTime == null) return true;
    return DateTime.now().difference(_lastProvidersLoadTime!) > _providersCacheDuration;
  }
  
  // 检查特定提供商的模型列表缓存是否有效
  bool _shouldRefreshModels(String provider) {
    // 如果状态中没有该提供商的模型数据，需要加载
    if (!state.modelGroups.containsKey(provider) || 
        state.modelGroups[provider]?.allModelsInfo.isEmpty == true) {
      return true;
    }
    
    // 检查缓存时间
    final lastLoadTime = _modelsCacheTime[provider];
    if (lastLoadTime == null) {
      // 模型数据已存在但没有记录时间戳，认为仍然有效，补记录当前时间
      _modelsCacheTime[provider] = DateTime.now();
      return false;
    }
    
    return DateTime.now().difference(lastLoadTime) > _modelsCacheValidDuration;
  }

  /// Helper方法：根据配置列表重新构建providerDefaultConfigs
  Map<String, UserAIModelConfigModel> _buildProviderDefaultConfigs(
      List<UserAIModelConfigModel> configs) {
    final Map<String, UserAIModelConfigModel> providerDefaultConfigs = {};
    
    // 按提供商分组
    final configsByProvider = <String, List<UserAIModelConfigModel>>{};
    for (final config in configs) {
      if (!configsByProvider.containsKey(config.provider)) {
        configsByProvider[config.provider] = [];
      }
      configsByProvider[config.provider]!.add(config);
    }
    
    // 为每个提供商选择一个默认配置
    configsByProvider.forEach((provider, providerConfigs) {
      // 优先选择默认配置，其次是已验证的配置，最后选择第一个配置
      final defaultConfig = providerConfigs.firstWhere(
        (c) => c.isDefault, 
        orElse: () => providerConfigs.firstWhere(
          (c) => c.isValidated,
          orElse: () => providerConfigs.first,
        ),
      );
      
      providerDefaultConfigs[provider] = defaultConfig;
    });
    
    return providerDefaultConfigs;
  }

  Future<void> _onLoadAiConfigs(
      LoadAiConfigs event, Emitter<AiConfigState> emit) async {
    // 如果用户已切换，强制刷新缓存与状态
    if (_lastLoadedUserId != null && _lastLoadedUserId != event.userId) {
      _lastConfigsLoadTime = null;
    }
    // 检查缓存是否有效
    if (!_shouldRefreshConfigs && state.configs.isNotEmpty) {
      AppLogger.d('AiConfigBloc', '使用缓存的配置数据，跳过重新加载');
      return;
    }
    
    emit(state.copyWith(status: AiConfigStatus.loading));
    try {
      final configs =
          await _repository.listConfigurations(userId: event.userId);
      
      _lastConfigsLoadTime = DateTime.now(); // 更新缓存时间
      
      // 按提供商分组用户配置
      final providerDefaultConfigs = _buildProviderDefaultConfigs(configs);
      
      emit(state.copyWith(
        status: AiConfigStatus.loaded,
        configs: configs,
        providerDefaultConfigs: providerDefaultConfigs,
        errorMessage: () => null, // Clear previous error
      ));
      // 记录当前加载用户
      _lastLoadedUserId = event.userId;
      
      AppLogger.i('AiConfigBloc', '配置加载成功，共${configs.length}个配置，已缓存');
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '加载配置失败', e, stackTrace);
      emit(state.copyWith(
          status: AiConfigStatus.error, errorMessage: () => e.toString()));
    }
  }

  // 重置事件：清空状态与所有相关缓存（用于登出/切换账号）
  void _onResetAiConfigs(ResetAiConfigs event, Emitter<AiConfigState> emit) {
    _lastConfigsLoadTime = null;
    _lastProvidersLoadTime = null;
    _modelsCacheTime.clear();
    _lastLoadedUserId = null;
    emit(const AiConfigState());
    AppLogger.i('AiConfigBloc', '已重置AI配置状态与缓存');
  }

  Future<void> _onLoadAvailableProviders(
      LoadAvailableProviders event, Emitter<AiConfigState> emit) async {
    // 如果已有缓存且未过期，直接返回
    if (!_shouldRefreshProviders && state.availableProviders.isNotEmpty) {
      AppLogger.d('AiConfigBloc', '使用缓存的提供商列表，跳过重新加载');
      return;
    }
    try {
      final providers = await _repository.listAvailableProviders();
      _lastProvidersLoadTime = DateTime.now();
      emit(state.copyWith(
        availableProviders: providers,
        errorMessage: () => null,
      ));
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '加载提供商失败', e, stackTrace);
      emit(state.copyWith(errorMessage: () => '加载提供商列表失败: \\${e.toString()}'));
    }
  }

  Future<void> _onLoadModelsForProvider(
      LoadModelsForProvider event, Emitter<AiConfigState> emit) async {
    // 检查缓存是否有效
    if (!_shouldRefreshModels(event.provider)) {
      AppLogger.d('AiConfigBloc', '使用缓存的模型数据，跳过重新加载: provider=${event.provider}');
      // 更新selectedProviderForModels以确保UI正确显示
      final cachedModelGroup = state.modelGroups[event.provider];
      if (cachedModelGroup != null) {
        emit(state.copyWith(
          selectedProviderForModels: event.provider,
          modelsForProviderInfo: cachedModelGroup.allModelsInfo,
        ));
        // 仍然触发GetProviderDefaultConfig以确保默认配置正确加载
        add(GetProviderDefaultConfig(provider: event.provider));
      }
      return;
    }
    
    emit(state.copyWith(
        modelsForProviderInfo: [], 
        selectedProviderForModels: event.provider,
        apiKeyTestSuccessProviderClearable: () => null,
        apiKeyTestErrorClearable: () => null,
      ));
    try {
      final models = await _repository.listModelsForProvider(event.provider);
      AppLogger.i('AiConfigBloc', '成功获取模型列表，provider=${event.provider}，模型数量=${models.length}');

      // 更新缓存时间
      _modelsCacheTime[event.provider] = DateTime.now();

      // Use the new factory for ModelInfo list
      final modelGroup = AIModelGroup.fromModelInfoList(event.provider, models); 
      final updatedModelGroups = Map<String, AIModelGroup>.from(state.modelGroups);
      updatedModelGroups[event.provider] = modelGroup;

      emit(state.copyWith(
        modelsForProviderInfo: models, 
        modelGroups: updatedModelGroups, // Update model groups
        errorMessage: () => null
      ));
      
      AppLogger.i('AiConfigBloc', '模型加载完成，已缓存，触发GetProviderDefaultConfig，provider=${event.provider}');
      add(GetProviderDefaultConfig(provider: event.provider));
    } catch (e, stackTrace) {
      AppLogger.e(
          'AiConfigBloc', '加载模型失败 for ${event.provider}', e, stackTrace);
      AppLogger.w('AiConfigBloc', '加载模型失败，provider=${event.provider}，错误：$e');
      emit(state.copyWith(
          modelsForProviderInfo: [], 
          errorMessage: () => '加载模型列表失败: ${e.toString()}'));
    }
  }

  Future<void> _onAddAiConfig(
      AddAiConfig event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      AppLogger.i('AiConfigBloc', '开始添加配置: provider=${event.provider}, modelName=${event.modelName}');
      
      final newConfig = await _repository.addConfiguration(
        userId: event.userId,
        provider: event.provider,
        modelName: event.modelName,
        alias: event.alias,
        apiKey: event.apiKey,
        apiEndpoint: event.apiEndpoint,
      );
      
      AppLogger.i('AiConfigBloc', '配置添加成功: configId=${newConfig.id}');
      
      // 直接更新列表，避免重复请求
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      currentConfigs.add(newConfig);
      
      // 重新构建providerDefaultConfigs
      final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
      
      // 使缓存失效，确保下次加载最新数据
      _lastConfigsLoadTime = null;
      
      emit(state.copyWith(
        actionStatus: AiConfigActionStatus.success,
        configs: currentConfigs,
        providerDefaultConfigs: providerDefaultConfigs,
      ));
      
      AppLogger.i('AiConfigBloc', '配置列表已更新，避免重复请求');
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '添加配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '添加失败: ${e.toString()}'));
    }
  }

  Future<void> _onUpdateAiConfig(
      UpdateAiConfig event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      final updatedConfig = await _repository.updateConfiguration(
        userId: event.userId,
        configId: event.configId,
        alias: event.alias,
        apiKey: event.apiKey,
        apiEndpoint: event.apiEndpoint,
      );
      // 更新列表中的特定项
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      final index = currentConfigs.indexWhere((c) => c.id == updatedConfig.id);
      if (index != -1) {
        currentConfigs[index] = updatedConfig;
        
        // 重新构建providerDefaultConfigs以确保UI正确显示
        final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
        
        emit(state.copyWith(
            actionStatus: AiConfigActionStatus.success,
            configs: currentConfigs,
            providerDefaultConfigs: providerDefaultConfigs));
      } else {
        // 如果找不到，最好还是重新加载
        emit(state.copyWith(actionStatus: AiConfigActionStatus.success));
        add(LoadAiConfigs(userId: event.userId));
      }
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '更新配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '更新失败: ${e.toString()}'));
    }
  }

  Future<void> _onDeleteAiConfig(
      DeleteAiConfig event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      await _repository.deleteConfiguration(
          userId: event.userId, configId: event.configId);
      // 从列表中移除
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      currentConfigs.removeWhere((c) => c.id == event.configId);
      
      // 重新构建providerDefaultConfigs以确保UI正确显示
      final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
      
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.success, 
          configs: currentConfigs,
          providerDefaultConfigs: providerDefaultConfigs));
      // 如果删除的是默认配置，可能需要清除默认状态或重新加载以确认新的默认（如果后端自动处理）
      // 这里暂时只移除
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '删除配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '删除失败: ${e.toString()}'));
    }
  }

  Future<void> _onValidateAiConfig(
      ValidateAiConfig event, Emitter<AiConfigState> emit) async {
    try {
      AppLogger.i('AiConfigBloc', '开始验证配置: configId=${event.configId}');
      
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.loading,
          actionErrorMessage: null,
          loadingConfigId: event.configId));

      final validatedConfig = await _repository.validateConfiguration(
          userId: event.userId, configId: event.configId);
      
      AppLogger.i('AiConfigBloc', '配置验证完成: configId=${event.configId}, isValidated=${validatedConfig.isValidated}');
      
      // 更新列表中的特定项
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      final index =
          currentConfigs.indexWhere((c) => c.id == validatedConfig.id);
      if (index != -1) {
        currentConfigs[index] = validatedConfig;
        
        // 重新构建providerDefaultConfigs以确保UI正确显示
        final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
        
        emit(state.copyWith(
            actionStatus: AiConfigActionStatus.success,
            configs: currentConfigs,
            providerDefaultConfigs: providerDefaultConfigs,
            loadingConfigId: null));
      } else {
        AppLogger.w('AiConfigBloc', '验证后找不到配置，触发重新加载');
        emit(state.copyWith(
            actionStatus: AiConfigActionStatus.success,
            loadingConfigId: null));
        add(LoadAiConfigs(userId: event.userId));
      }
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '验证配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '验证请求失败: ${e.toString()}',
          loadingConfigId: null));
    }
  }

  Future<void> _onSetDefaultAiConfig(
      SetDefaultAiConfig event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      AppLogger.i('AiConfigBloc', '开始设置默认配置: configId=${event.configId}');
      
      final newDefaultConfig = await _repository.setDefaultConfiguration(
          userId: event.userId, configId: event.configId);
      
      // 更新所有配置的默认状态
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      for (int i = 0; i < currentConfigs.length; i++) {
        if (currentConfigs[i].id == event.configId) {
          currentConfigs[i] = newDefaultConfig;
        } else if (currentConfigs[i].isDefault) {
          // 取消其他配置的默认状态
          currentConfigs[i] = currentConfigs[i].copyWith(isDefault: false);
        }
      }
      
      // 重新构建providerDefaultConfigs
      final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
      
      // 使缓存失效
      _lastConfigsLoadTime = null;
      
      emit(state.copyWith(
        actionStatus: AiConfigActionStatus.success,
        configs: currentConfigs,
        providerDefaultConfigs: providerDefaultConfigs,
      ));
      
      AppLogger.i('AiConfigBloc', '默认配置设置成功，避免重复请求');
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '设置默认配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '设置默认失败: ${e.toString()}'));
    }
  }

  Future<void> _onSetToolDefaultAiConfig(
      SetToolDefaultAiConfig event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      AppLogger.i('AiConfigBloc', '开始设置工具默认配置: configId=${event.configId}');

      // 调用仓库设置工具默认
      await _repository.setToolDefaultConfiguration(
          userId: event.userId, configId: event.configId);

      // 更新所有配置的工具默认状态
      final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
      for (int i = 0; i < currentConfigs.length; i++) {
        if (currentConfigs[i].id == event.configId) {
          currentConfigs[i] = currentConfigs[i].copyWith(isToolDefault: true);
        } else if (currentConfigs[i].isToolDefault) {
          currentConfigs[i] = currentConfigs[i].copyWith(isToolDefault: false);
        }
      }

      // 重新构建providerDefaultConfigs（不影响该映射，但保持一致流程）
      final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);

      _lastConfigsLoadTime = null;

      emit(state.copyWith(
        actionStatus: AiConfigActionStatus.success,
        configs: currentConfigs,
        providerDefaultConfigs: providerDefaultConfigs,
      ));
      AppLogger.i('AiConfigBloc', '工具默认配置设置成功');
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '设置工具默认配置失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '设置工具默认失败: ${e.toString()}'));
    }
  }

  void _onClearProviderModels(
      ClearProviderModels event, Emitter<AiConfigState> emit) {
    // 清除模型列表和当前选中的提供商
    emit(state.copyWith(
      clearModels: true,
      // 保留模型分组信息，因为它可能在其他地方被使用
      // 如果需要清除特定提供商的模型分组，可以在这里处理
    ));
  }

  // 根据provider查找第一个可用的配置，用于显示该提供商的API密钥和URL
  Future<void> _onGetProviderDefaultConfig(
      GetProviderDefaultConfig event, Emitter<AiConfigState> emit) async {
    final provider = event.provider;
    //print('⚠️ 开始处理GetProviderDefaultConfig事件，provider=$provider');
    
    // 获取当前状态的providerDefaultConfigs副本
    final providerDefaultConfigs = Map<String, UserAIModelConfigModel>.from(state.providerDefaultConfigs);
    
    // 从已加载的配置中查找
    final providerConfigs = state.configs.where((c) => c.provider == provider).toList();
    //print('⚠️ 查找provider=$provider的配置，找到${providerConfigs.length}个配置');
    
    if (providerConfigs.isEmpty) {
      //print('⚠️ 没有找到provider=$provider的配置');
      // 没有找到该提供商的配置，从Map中移除这个提供商的配置（如果有）
      if (providerDefaultConfigs.containsKey(provider)) {
        providerDefaultConfigs.remove(provider);
        emit(state.copyWith(
          providerDefaultConfigs: providerDefaultConfigs,
        ));
        //print('⚠️ 已从providerDefaultConfigs中移除provider=$provider的配置');
      }
      return;
    }
    
    // 首先寻找默认的
    final defaultConfig = providerConfigs.firstWhere(
      (c) => c.isDefault, 
      orElse: () => providerConfigs.firstWhere(
        (c) => c.isValidated,
        orElse: () => providerConfigs.first,
      ),
    );
    
    //print('⚠️ 找到provider=$provider的默认配置，id=${defaultConfig.id}，apiEndpoint=${defaultConfig.apiEndpoint}，hasApiKey=${defaultConfig.apiKey != null}');
    
    // 更新或添加该提供商的默认配置
    providerDefaultConfigs[provider] = defaultConfig;
    
    // 更新状态
    emit(state.copyWith(
      providerDefaultConfigs: providerDefaultConfigs,
    ));
    
    //print('⚠️ 已更新状态中的providerDefaultConfigs，当前包含的提供商：${providerDefaultConfigs.keys.join(", ")}');
  }

  // 处理加载API密钥的事件
  Future<void> _onLoadApiKeyForConfig(
      LoadApiKeyForConfig event, Emitter<AiConfigState> emit) async {
    try {
      // 从已加载的配置中查找
      final config = state.configs.firstWhereOrNull(
        (config) => config.id == event.configId
      );
      
      if (config != null && config.apiKey != null) {
        // 如果已加载的配置中有API密钥，直接使用
        // event.onApiKeyLoaded(config.apiKey!); // Commenting out: ValueGetter<void> takes no arguments
        //print("API Key found in state for ${event.configId}");
        // TODO: Decide how to actually return/use this key - maybe emit a state?
        return;
      }
      
      // 如果没有找到配置或者没有API密钥，提示用户手动输入
      // event.onApiKeyLoaded("请手动输入API密钥"); // Commenting out: ValueGetter<void> takes no arguments
      //print("API Key NOT found in state for ${event.configId}");
       // TODO: Decide how to handle missing key - maybe emit an error state?
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '获取API密钥失败', e, stackTrace);
      // 如果失败，返回一个错误提示
      // event.onApiKeyLoaded("获取失败，请手动输入"); // Commenting out: ValueGetter<void> takes no arguments
      //print("Error loading API Key for ${event.configId}: $e");
       // TODO: Decide how to handle error - maybe emit an error state?
    }
  }

  // --- Handlers for New Events ---

  Future<void> _onLoadProviderCapability(
      LoadProviderCapability event, Emitter<AiConfigState> emit) async {
    // Reset previous capability and test status for the new provider
    emit(state.copyWith(
      providerCapabilityClearable: () => null,
      isTestingApiKey: false,
      apiKeyTestSuccessProviderClearable: () => null,
      apiKeyTestErrorClearable: () => null,
    ));
    try {
      // 调用repository方法获取提供商能力
      final capability = await _repository.getProviderCapability(event.providerName);
      
      AppLogger.i('AiConfigBloc', '加载提供商 ${event.providerName} 能力成功: $capability');
      emit(state.copyWith(providerCapability: capability));

      // --- 修改开始 ---
      // bool shouldLoadWithKey = false; // 已不再使用
      UserAIModelConfigModel? defaultConfig;

      // 优先从 providerDefaultConfigs 获取，因为它是为这个场景设计的
      defaultConfig = state.providerDefaultConfigs[event.providerName];
      
      // 如果默认配置里没key，再尝试从完整列表里捞一个有效的 (可能不是最优选择，但作为后备)
      // if (defaultConfig == null || defaultConfig.apiKey == null || defaultConfig.apiKey!.isEmpty) {
      //    final providerConfigs = state.configs.where((c) => c.provider == event.providerName).toList();
      //    if (providerConfigs.isNotEmpty) {
      //      defaultConfig = providerConfigs.firstWhere(
      //        (c) => c.isDefault && c.apiKey != null && c.apiKey!.isNotEmpty,
      //        orElse: () => providerConfigs.firstWhere(
      //          (c) => c.isValidated && c.apiKey != null && c.apiKey!.isNotEmpty,
      //          orElse: () => providerConfigs.firstWhere(
      //             (c) => c.apiKey != null && c.apiKey!.isNotEmpty,
      //             orElse: () => providerConfigs.first // Last resort: first config even without key
      //           )
      //        )
      //      );
      //    }
      // }


      if (capability == ModelListingCapability.listingWithKey) {
        // 检查找到的配置（优先是 providerDefaultConfigs 里的）是否有有效的 API Key
        if (defaultConfig != null && defaultConfig.apiKey != null && defaultConfig.apiKey!.isNotEmpty) {
           // 注释掉自动验证逻辑，避免在新建模式下自动验证API Key
           // shouldLoadWithKey = true;
           AppLogger.i('AiConfigBloc', 'Provider ${event.providerName} 需要 Key，找到已配置的 Key，但不自动验证，将加载默认模型列表');
        } else {
           AppLogger.i('AiConfigBloc', 'Provider ${event.providerName} 需要 Key，但未找到带 Key 的默认/有效配置，将加载默认模型');
        }
      } else {
         AppLogger.i('AiConfigBloc', 'Provider ${event.providerName} 不需要 Key 或不支持列表，将加载默认模型');
      }

      // 清除之前的测试状态和错误信息，避免残留
      emit(state.copyWith(
          apiKeyTestSuccessProviderClearable: () => null,
          apiKeyTestErrorClearable: () => null,
          isTestingApiKey: false // 不自动测试API Key
      ));

      // 统一使用LoadModelsForProvider加载模型列表，不自动验证API Key
      AppLogger.i('AiConfigBloc', '触发加载 ${event.providerName} 的默认模型列表 (LoadModelsForProvider)');
      
      // 添加事件去重或状态检查，避免无限循环
      // 例如：检查是否已经在加载该提供商的模型
      if (state.selectedProviderForModels != event.providerName || state.modelsForProviderInfo.isEmpty) {
         add(LoadModelsForProvider(provider: event.providerName));
      } else {
         AppLogger.i('AiConfigBloc', '该提供商模型已加载，跳过重复请求');
      }
      // --- 修改结束 ---

    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '加载提供商 ${event.providerName} 能力失败', e, stackTrace);
      emit(state.copyWith(errorMessage: () => '加载提供商能力失败: ${e.toString()}'));
      // 即使能力加载失败，也尝试加载默认模型列表，避免界面空白
      AppLogger.w('AiConfigBloc', '能力加载失败，仍尝试加载 ${event.providerName} 的默认模型列表');
      add(LoadModelsForProvider(provider: event.providerName));
    }
  }

  Future<void> _onTestApiKey(
      TestApiKey event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
      isTestingApiKey: true,
      apiKeyTestSuccessProviderClearable: () => null,
      apiKeyTestErrorClearable: () => null,
    ));
    try {
      final ok = await _repository.testApiKey(
        provider: event.providerName,
        apiKey: event.apiKey,
        apiEndpoint: event.apiEndpoint,
        modelName: event.modelName, // 传递模型名称
      );

      if (ok) {
        AppLogger.i('AiConfigBloc', 'API Key 测试通过 for ${event.providerName}${event.modelName != null ? " 模型: ${event.modelName}" : ""}');
        emit(state.copyWith(
          isTestingApiKey: false,
          apiKeyTestSuccessProvider: event.providerName,
        ));
      } else {
        AppLogger.w('AiConfigBloc', 'API Key 测试未通过 for ${event.providerName}${event.modelName != null ? " 模型: ${event.modelName}" : ""}');
        emit(state.copyWith(
          isTestingApiKey: false,
          apiKeyTestError: 'API Key 无效或无权限',
        ));
      }
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '测试 API Key 异常 for ${event.providerName}${event.modelName != null ? " 模型: ${event.modelName}" : ""}', e, stackTrace);
      emit(state.copyWith(
        isTestingApiKey: false,
        apiKeyTestError: 'API Key 测试失败: ${e.toString()}',
      ));
    }
  }

  // Handler to clear the API key test error
  void _onClearApiKeyTestError(
      ClearApiKeyTestError event, Emitter<AiConfigState> emit) {
    // Use ValueGetter to explicitly set the error to null
    emit(state.copyWith(apiKeyTestErrorClearable: () => null));
  }

  // Optional: Modify _onLoadModelsForProvider if needed
  // Example: Reset API key test status when models are loaded without a key test
  // Future<void> _onLoadModelsForProvider(
  //     LoadModelsForProvider event, Emitter<AiConfigState> emit) async {
  //   emit(state.copyWith(
  //       modelsForProvider: [],
  //       selectedProviderForModels: event.provider,
  //       // Reset test status if loading models without key
  //       apiKeyTestSuccessProviderClearable: () => null,
  //       apiKeyTestErrorClearable: () => null
  //     ));
  //    // ... rest of the existing logic ...
  // }

  Future<void> _onAddCustomModelAndValidate(
      AddCustomModelAndValidate event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
        actionStatus: AiConfigActionStatus.loading,
        actionErrorMessage: () => null));
    try {
      AppLogger.i('AiConfigBloc', '开始添加自定义模型并验证: provider=${event.provider}, modelName=${event.modelName}');
      
      // 首先添加配置
      final newConfig = await _repository.addConfiguration(
        userId: event.userId,
        provider: event.provider,
        modelName: event.modelName,
        alias: event.alias,
        apiKey: event.apiKey,
        apiEndpoint: event.apiEndpoint,
      );
      
      AppLogger.i('AiConfigBloc', '自定义模型添加成功: configId=${newConfig.id}');
      
      // 立即验证配置
      try {
        final validatedConfig = await _repository.validateConfiguration(
          userId: event.userId, 
          configId: newConfig.id,
        );
        
        AppLogger.i('AiConfigBloc', '自定义模型验证完成: configId=${newConfig.id}, isValidated=${validatedConfig.isValidated}');
        
        // 直接更新列表，避免重复请求
        final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
        currentConfigs.add(validatedConfig);
        
        // 重新构建providerDefaultConfigs
        final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
        
        // 使缓存失效，确保下次加载最新数据
        _lastConfigsLoadTime = null;
        
        emit(state.copyWith(
          actionStatus: AiConfigActionStatus.success,
          configs: currentConfigs,
          providerDefaultConfigs: providerDefaultConfigs,
        ));
        
        AppLogger.i('AiConfigBloc', '自定义模型添加和验证完成，列表已更新');
        
      } catch (validateError) {
        AppLogger.w('AiConfigBloc', '自定义模型验证失败，但配置已添加: ${validateError.toString()}');
        
        // 验证失败，但配置已添加，仍然更新列表
        final currentConfigs = List<UserAIModelConfigModel>.from(state.configs);
        currentConfigs.add(newConfig);
        
        final providerDefaultConfigs = _buildProviderDefaultConfigs(currentConfigs);
        _lastConfigsLoadTime = null;
        
        emit(state.copyWith(
          actionStatus: AiConfigActionStatus.success,
          configs: currentConfigs,
          providerDefaultConfigs: providerDefaultConfigs,
        ));
      }
      
    } catch (e, stackTrace) {
      AppLogger.e('AiConfigBloc', '添加自定义模型失败', e, stackTrace);
      emit(state.copyWith(
          actionStatus: AiConfigActionStatus.error,
          actionErrorMessage: () => '添加自定义模型失败: ${e.toString()}'));
    }
  }

  void _onClearModelsCache(ClearModelsCache event, Emitter<AiConfigState> emit) {
    if (event.provider != null) {
      // 清除特定提供商的缓存
      _modelsCacheTime.remove(event.provider);
      AppLogger.i('AiConfigBloc', '已清除提供商 ${event.provider} 的模型缓存');
    } else {
      // 清除所有模型缓存
      _modelsCacheTime.clear();
      AppLogger.i('AiConfigBloc', '已清除所有模型缓存');
    }
  }

  // 直连获取模型列表（前端直连供应商/中转服务）
  Future<void> _onLoadModelsDirect(
      LoadModelsDirect event, Emitter<AiConfigState> emit) async {
    emit(state.copyWith(
      modelsForProviderInfo: [],
      selectedProviderForModels: event.providerName,
      apiKeyTestSuccessProviderClearable: () => null,
      apiKeyTestErrorClearable: () => null,
    ));
    try {
      final svc = ModelListingService();
      final models = await svc.listModelsDirect(
        provider: event.providerName,
        apiKey: event.apiKey,
        apiEndpoint: event.apiEndpoint,
      );

      // 更新缓存时间（沿用后端路径的缓存字段）
      _modelsCacheTime[event.providerName] = DateTime.now();

      final modelGroup = AIModelGroup.fromModelInfoList(event.providerName, models);
      final updatedModelGroups = Map<String, AIModelGroup>.from(state.modelGroups);
      updatedModelGroups[event.providerName] = modelGroup;

      emit(state.copyWith(
        modelsForProviderInfo: models,
        modelGroups: updatedModelGroups,
        selectedProviderForModels: event.providerName,
        errorMessage: () => null,
      ));
    } catch (e, s) {
      AppLogger.e('AiConfigBloc', '直连获取模型失败 for ${event.providerName}', e, s);
      // 失败时不抛出，回退到后端拉取
      add(LoadModelsForProvider(provider: event.providerName));
    }
  }
}
