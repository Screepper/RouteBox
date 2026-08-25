from typing import List, Dict

import uvicorn
from fastapi import FastAPI
from fastapi import FastAPI, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from entity.anomaly_info import AnomalyInfo
from entity.system_network_log import SystemNetworkLog
from ad_service import ADService
from algorithm.meted import METED

import config


def create_app():
    myapp = FastAPI(title="Anomaly Detection", info="Anomaly Detection")
    detector = METED(plot_save=False)

    ad_service = ADService(detector=detector)

    class DetectRequest(BaseModel):
        currentMap: Dict[int, List[SystemNetworkLog]]
        recentMap: Dict[int, List[SystemNetworkLog]]
        periodMap: Dict[int, List[SystemNetworkLog]]

    @myapp.post("/detect", summary="detect", response_model=List[AnomalyInfo])
    def detect(request: DetectRequest):
        current_dict, recent_dict, period_dict = (
            request.currentMap,
            request.recentMap,
            request.periodMap,
        )

        return ad_service.detect(current_dict, recent_dict, period_dict)

    @myapp.get("/health", summary="Health Check", tags=["System"])
    def health_check():
        try:
            return JSONResponse(
                status_code=status.HTTP_200_OK, content={"status": "UP"}
            )
        except Exception as e:
            return JSONResponse(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                content={"status": "DOWN"},
            )

    return myapp


if __name__ == "__main__":
    app = create_app()

    # configure your host and port
    uvicorn.run(app, host="0.0.0.0", port=config.SERVICE_PORT, reload=False)
