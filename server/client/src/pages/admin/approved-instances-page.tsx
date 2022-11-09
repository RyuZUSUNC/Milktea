import AppBarLayout from "../../layout/AppBarLayout";
import BodyLayout from "../../layout/BodyLayout";
import InstancesStatePage from "./components/instances-state-page";


const ApprovedInstancesPage: React.FC = () => {
    return <BodyLayout topAppBar={
        <AppBarLayout>
          公開承認済みインスタンス
        </AppBarLayout>
      }>
        <InstancesStatePage filterType="approved" />
      </BodyLayout>
}
export default ApprovedInstancesPage