import 'dart:convert';
import 'package:equatable/equatable.dart';
import 'package:ainoval/models/ai_context_tracking.dart';
import 'package:ainoval/models/setting_relationship_type.dart';

/// 小说设定条目模型
class NovelSettingItem extends Equatable {
  final String? id;
  final String? novelId;
  final String? userId;
  final String name;
  final String? type;
  final String? content;
  final String? description;
  final Map<String, String>? attributes;
  final String? imageUrl;
  final List<SettingRelationship>? relationships;
  final List<String>? sceneIds;
  final int? priority;
  final String? generatedBy;
  final List<String>? tags;
  final String? status;
  final List<double>? vector;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final bool isAiSuggestion;
  final Map<String, dynamic>? metadata;
  
  // ==================== 父子关系字段 ====================
  
  /// 父设定ID（建立层级关系的核心字段）
  final String? parentId;
  
  /// 子设定ID列表（冗余字段，用于快速查询）
  final List<String>? childrenIds;
  
  // ==================== AI上下文追踪字段 ====================
  
  /// 名称/别名追踪设置
  final NameAliasTracking nameAliasTracking;
  
  /// AI上下文包含设置
  final AIContextTracking aiContextTracking;
  
  /// 设定引用更新设置
  final SettingReferenceUpdate referenceUpdatePolicy;

  const NovelSettingItem({
    this.id,
    this.novelId,
    this.userId,
    required this.name,
    this.type,
    this.content = "",
    this.description,
    this.attributes,
    this.imageUrl,
    this.relationships,
    this.sceneIds,
    this.priority,
    this.generatedBy,
    this.tags,
    this.status,
    this.vector,
    this.createdAt,
    this.updatedAt,
    this.isAiSuggestion = false,
    this.metadata,
    this.parentId,
    this.childrenIds,
    this.nameAliasTracking = NameAliasTracking.track,
    this.aiContextTracking = AIContextTracking.detected,
    this.referenceUpdatePolicy = SettingReferenceUpdate.ask,
  });

  factory NovelSettingItem.fromJson(Map<String, dynamic> json) {
    List<SettingRelationship>? relationships;
    if (json['relationships'] != null && json['relationships'] is List) {
      relationships = (json['relationships'] as List)
          .map((e) => SettingRelationship.fromJson(e as Map<String, dynamic>))
          .toList();
    }

    Map<String, String>? attributesMap;
    if (json['attributes'] != null && json['attributes'] is Map) {
      attributesMap = Map<String, String>.from(json['attributes'] as Map);
    }

    List<String>? tagsList;
    if (json['tags'] != null && json['tags'] is List) {
      tagsList = List<String>.from(json['tags'] as List);
    }
    
    List<String>? sceneIdsList;
    if (json['sceneIds'] != null && json['sceneIds'] is List) {
      sceneIdsList = List<String>.from(json['sceneIds'] as List);
    }
    
    List<String>? childrenIdsList;
    if (json['childrenIds'] != null && json['childrenIds'] is List) {
      childrenIdsList = List<String>.from(json['childrenIds'] as List);
    }

    List<double>? vectorList;
    if (json['vector'] != null && json['vector'] is List) {
      vectorList = (json['vector'] as List).map((e) => (e as num).toDouble()).toList();
    }
    
    Map<String, dynamic>? metadataMap;
    if (json['metadata'] != null && json['metadata'] is Map) {
      metadataMap = Map<String, dynamic>.from(json['metadata'] as Map);
    }

    return NovelSettingItem(
      id: json['id'] as String?,
      novelId: json['novelId'] as String?,
      userId: json['userId'] as String?,
      name: json['name'] as String? ?? '未命名设定',
      type: json['type'] as String?,
      content: json['content'] as String?,
      description: json['description'] as String?,
      attributes: attributesMap,
      imageUrl: json['imageUrl'] as String?,
      relationships: relationships,
      sceneIds: sceneIdsList,
      priority: json['priority'] as int?,
      status: json['status'] as String?,
      generatedBy: json['generatedBy'] as String?,
      tags: tagsList,
      vector: vectorList,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) : null,
      isAiSuggestion: json['isAiSuggestion'] as bool? ?? false,
      metadata: metadataMap,
      parentId: json['parentId'] as String?,
      childrenIds: childrenIdsList,
      nameAliasTracking: NameAliasTracking.fromValue(json['nameAliasTracking'] as String?),
      aiContextTracking: AIContextTracking.fromValue(json['aiContextTracking'] as String?),
      referenceUpdatePolicy: SettingReferenceUpdate.fromValue(json['referenceUpdatePolicy'] as String?),
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    if (id != null) data['id'] = id;
    if (novelId != null) data['novelId'] = novelId;
    if (userId != null) data['userId'] = userId;
    data['name'] = name;
    if (type != null) data['type'] = type;
    if (content != null) data['content'] = content;
    if (description != null) data['description'] = description;
    if (attributes != null) data['attributes'] = attributes;
    if (imageUrl != null) data['imageUrl'] = imageUrl;
    if (relationships != null) {
      data['relationships'] = relationships!.map((e) => e.toJson()).toList();
    }
    if (sceneIds != null) data['sceneIds'] = sceneIds;
    if (priority != null) data['priority'] = priority;
    if (generatedBy != null) data['generatedBy'] = generatedBy;
    if (tags != null) data['tags'] = tags;
    if (status != null) data['status'] = status;
    if (vector != null) data['vector'] = vector;
    if (createdAt != null) data['createdAt'] = createdAt!.toIso8601String();
    if (updatedAt != null) data['updatedAt'] = updatedAt!.toIso8601String();
    data['isAiSuggestion'] = isAiSuggestion;
    if (metadata != null) data['metadata'] = metadata;
    if (parentId != null) data['parentId'] = parentId;
    if (childrenIds != null) data['childrenIds'] = childrenIds;
    data['nameAliasTracking'] = nameAliasTracking.value;
    data['aiContextTracking'] = aiContextTracking.value;
    data['referenceUpdatePolicy'] = referenceUpdatePolicy.value;
    return data;
  }

  NovelSettingItem copyWith({
    String? id,
    String? novelId,
    String? userId,
    String? name,
    String? type,
    String? content,
    String? description,
    Map<String, String>? attributes,
    String? imageUrl,
    List<SettingRelationship>? relationships,
    List<String>? sceneIds,
    int? priority,
    String? generatedBy,
    List<String>? tags,
    String? status,
    List<double>? vector,
    DateTime? createdAt,
    DateTime? updatedAt,
    bool? isAiSuggestion,
    Map<String, dynamic>? metadata,
    String? parentId,
    List<String>? childrenIds,
    NameAliasTracking? nameAliasTracking,
    AIContextTracking? aiContextTracking,
    SettingReferenceUpdate? referenceUpdatePolicy,
  }) {
    return NovelSettingItem(
      id: id ?? this.id,
      novelId: novelId ?? this.novelId,
      userId: userId ?? this.userId,
      name: name ?? this.name,
      type: type ?? this.type,
      content: content ?? this.content,
      description: description ?? this.description,
      attributes: attributes ?? this.attributes,
      imageUrl: imageUrl ?? this.imageUrl,
      relationships: relationships ?? this.relationships,
      sceneIds: sceneIds ?? this.sceneIds,
      priority: priority ?? this.priority,
      generatedBy: generatedBy ?? this.generatedBy,
      tags: tags ?? this.tags,
      status: status ?? this.status,
      vector: vector ?? this.vector,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      isAiSuggestion: isAiSuggestion ?? this.isAiSuggestion,
      metadata: metadata ?? this.metadata,
      parentId: parentId ?? this.parentId,
      childrenIds: childrenIds ?? this.childrenIds,
      nameAliasTracking: nameAliasTracking ?? this.nameAliasTracking,
      aiContextTracking: aiContextTracking ?? this.aiContextTracking,
      referenceUpdatePolicy: referenceUpdatePolicy ?? this.referenceUpdatePolicy,
    );
  }

  @override
  List<Object?> get props => [
    id, novelId, userId, name, type, content, description, attributes, 
    imageUrl, relationships, sceneIds, priority, generatedBy, tags, status, 
    vector, createdAt, updatedAt, isAiSuggestion, metadata, parentId, childrenIds,
    nameAliasTracking, aiContextTracking, referenceUpdatePolicy
  ];

  @override
  String toString() {
    return jsonEncode(toJson());
  }
}

/// 设定关系模型
class SettingRelationship extends Equatable {
  final String targetItemId;
  final SettingRelationshipType type;
  final String? description;
  final int? strength;
  final String? direction;
  final DateTime? createdAt;
  final Map<String, dynamic>? attributes;

  const SettingRelationship({
    required this.targetItemId,
    required this.type,
    this.description,
    this.strength,
    this.direction,
    this.createdAt,
    this.attributes,
  });

  factory SettingRelationship.fromJson(Map<String, dynamic> json) {
    return SettingRelationship(
      targetItemId: json['targetItemId'] as String,
      type: SettingRelationshipType.fromValue(json['type'] as String),
      description: json['description'] as String?,
      strength: json['strength'] as int?,
      direction: json['direction'] as String?,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      attributes: json['attributes'] != null ? Map<String, dynamic>.from(json['attributes']) : null,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['targetItemId'] = targetItemId;
    data['type'] = type.value;
    if (description != null) data['description'] = description;
    if (strength != null) data['strength'] = strength;
    if (direction != null) data['direction'] = direction;
    if (createdAt != null) data['createdAt'] = createdAt!.toIso8601String();
    if (attributes != null) data['attributes'] = attributes;
    return data;
  }

  @override
  List<Object?> get props => [targetItemId, type, description, strength, direction, createdAt, attributes];
} 