package com.scenedeck.android.core.database

/** Called inside WorkspaceSceneDao.rename's transaction, even when no curation row exists. */
internal suspend fun WorkspaceSceneDao.renameDeckReferences(
    profileId: Long,
    collectionName: String,
    oldName: String,
    newName: String,
) {
    val byDeck = membersAffectedByRename(profileId, collectionName, oldName).groupBy { it.deckId }
    if (byDeck.isEmpty()) return
    val renamed = byDeck.values.flatMap { members -> renamedMembers(members, oldName, newName) }
    clearMembersForRename(profileId, collectionName, byDeck.keys.toList())
    insertRenamedMembers(renamed)
}

/** An existing destination keeps its position; all resulting positions are dense. */
private fun renamedMembers(
    members: List<NamedDeckMemberEntity>,
    old: String,
    new: String,
): List<NamedDeckMemberEntity> {
    val destinationExists = members.any { it.sceneName == new }
    return members
        .filterNot { destinationExists && it.sceneName == old }
        .mapIndexed { index, member ->
            member.copy(
                sceneName = if (member.sceneName == old) new else member.sceneName,
                sortOrder = index,
            )
        }
}
