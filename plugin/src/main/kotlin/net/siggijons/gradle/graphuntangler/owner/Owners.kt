package net.siggijons.gradle.graphuntangler.owner

class Owners(
    val ownerMap: Map<String, String>,
) {
    private val splitMap: Map<List<String>, String> = ownerMap.mapKeys {
        it.key.split(":")
    }

    fun findOwner(
        project: String
    ): String? {
        return findOwnerRaw(project)?.split("/", limit = 2)?.lastOrNull()
    }

    private fun findOwnerRaw(
        project: String
    ): String? {
        return ownerMap[project] ?: findPartialOwner(project)
    }

    private fun findPartialOwner(project: String): String? {
        val splits = project.split(":")
        for (i in splits.size.downTo(1)) {
            val match = splitMap[splits.take(i)]
            if (match != null) return match
        }
        return null
    }
}