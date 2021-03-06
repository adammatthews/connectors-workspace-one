/*
 * Copyright © 2017 VMware, Inc. All Rights Reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */

package com.vmware.connectors.servicenow;

import org.pojomatic.Pojomatic;
import org.pojomatic.annotations.AutoProperty;

@AutoProperty
class ApprovalRequestWithInfo extends ApprovalRequest {

    private final Request info;

    /**
     * @param request The {@link ApprovalRequest} information from the sysapproval_approver record.
     * @param info The request infofrom the sc_request record.
     */
    ApprovalRequestWithInfo(
            ApprovalRequest request,
            Request info
    ) {
        super(
                request.getRequestSysId(),
                request.getApprovalSysId(),
                request.getComments(),
                request.getDueDate(),
                request.getCreatedBy()
        );
        this.info = info;
    }

    /**
     * @return The request info from the sc_request record.
     */
    Request getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return Pojomatic.toString(this);
    }

}
