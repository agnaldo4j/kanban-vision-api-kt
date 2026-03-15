package com.kanbanvision.domain.model.workitem

import com.kanbanvision.domain.model.valueobjects.WorkItemId

data class WorkItem(
    val id: WorkItemId,
    val title: String,
    val serviceClass: ServiceClass,
    val state: WorkItemState,
    val agingDays: Int,
) {
    init {
        require(title.isNotBlank()) { "WorkItem title must not be blank" }
        require(agingDays >= 0) { "WorkItem agingDays must be non-negative" }
    }

    companion object {
        fun create(
            title: String,
            serviceClass: ServiceClass = ServiceClass.STANDARD,
        ): WorkItem =
            WorkItem(
                id = WorkItemId.generate(),
                title = title,
                serviceClass = serviceClass,
                state = WorkItemState.TODO,
                agingDays = 0,
            )
    }

    fun advance(): WorkItem =
        when (state) {
            WorkItemState.TODO -> copy(state = WorkItemState.IN_PROGRESS)
            WorkItemState.IN_PROGRESS -> copy(state = WorkItemState.DONE)
            WorkItemState.BLOCKED -> copy(state = WorkItemState.IN_PROGRESS)
            WorkItemState.DONE -> this
        }

    fun block(): WorkItem {
        require(state == WorkItemState.IN_PROGRESS) { "Only IN_PROGRESS items can be blocked" }
        return copy(state = WorkItemState.BLOCKED)
    }

    fun incrementAge(): WorkItem = copy(agingDays = agingDays + 1)
}
